package com.wenxing.matterreassembler.block;

import com.wenxing.matterreassembler.config.MatterReassemblerConfig;
import com.wenxing.matterreassembler.energy.EnergyMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import appeng.api.inventories.InternalInventory;
import appeng.blockentity.grid.AENetworkInvBlockEntity;

/**
 * 物质重组器的方块实体。
 *
 * <p>职责边界：本类只负责「存电 + 开关 + 在线状态 + 把能量接口按面暴露」。
 * 合成接管的判定与结算在后续波次实现（{@code DESIGN-1.20.1.md} §6）。
 *
 * <p>刻意不做的事：
 * <ul>
 *   <li>不在 tick 里主动拉/推能量——一切能量增减都必须由外部机器调用接口触发；</li>
 *   <li>不要求 AE 频道、不消耗 AE 电量，只借网格节点判断「是否已接入网络」。</li>
 * </ul>
 */
public class MatterReassemblerBlockEntity extends AENetworkInvBlockEntity {
    public static final String NBT_ENERGY = "Energy";
    public static final String NBT_ENABLED = "Enabled";

    /** 在线状态复核间隔（刻）：换取零网络包，代价是外观最多延迟 0.5 秒跟上。 */
    private static final int ONLINE_RECHECK_INTERVAL = 10;

    /** 内部电量池。long 承载，对外只能经 {@link EnergyMath} 钳制成 int。 */
    private long energy;

    /** 开关状态：服务端权威，关闭时拒绝一切能量交互与合成接管。 */
    private boolean enabled = true;

    private final LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(EnergyHandler::new);

    public MatterReassemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // 不占频道、不吃 AE 电量：它只是挂在网上读「是否在线」，不是用电器。
        getMainNode().setIdlePowerUsage(0.0);
    }

    /**
     * Forge 的方块实体工厂入口。
     *
     * <p>AE2 基类要求构造器拿到自己的 {@code BlockEntityType}，而工厂签名只有 {@code (pos, state)}，
     * 所以在这里从注册表取回来——此时注册早已完成（工厂只在区块加载时被调用）。
     * 这段取值不能写进 {@code MATTER_REASSEMBLER_BE} 的字段初始化器，那会构成「初始化程序自引用」。
     */
    public static MatterReassemblerBlockEntity create(BlockPos pos, BlockState state) {
        return new MatterReassemblerBlockEntity(
                com.wenxing.matterreassembler.MatterReassembler.MATTER_REASSEMBLER_BE.get(), pos, state);
    }

    /** BlockEntityTicker 入口（由方块在 setBlockEntity 时注册）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, MatterReassemblerBlockEntity be) {
        be.serverTick();
    }

    private void serverTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (level.getGameTime() % ONLINE_RECHECK_INTERVAL != 0L) {
            return;
        }
        boolean active = getMainNode().isActive();
        BlockState state = getBlockState();
        // 外观由方块状态驱动：属性一变，客户端与渲染自动跟上，不用发自定义包。
        if (state.hasProperty(MatterReassemblerBlock.ONLINE) && state.getValue(MatterReassemblerBlock.ONLINE) != active) {
            level.setBlock(worldPosition, state.setValue(MatterReassemblerBlock.ONLINE, active), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------
    // 开关
    // ------------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    /** 反转开关。只有服务端调用才有意义（调用方已做 isClientSide 早退）。 */
    public void toggleActive() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean value) {
        if (enabled == value) {
            return;
        }
        enabled = value;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(MatterReassemblerBlock.ENABLED)) {
                level.setBlock(worldPosition, state.setValue(MatterReassemblerBlock.ENABLED, value), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ------------------------------------------------------------------
    // 电量池（供能量接口与后续的覆盖板注入共用）
    // ------------------------------------------------------------------

    public long getStoredEnergy() {
        return energy;
    }

    /** 直接写池：仅用于存档读取与覆盖板注入，越界值收敛到 [0, Long.MAX_VALUE]。 */
    public void setStoredEnergy(long value) {
        long clamped = Math.max(0L, value);
        if (energy != clamped) {
            energy = clamped;
            setChanged();
        }
    }

    /**
     * 追加电量并返回实际接收量。覆盖板走这条路，因此**不受 int 上限约束**——
     * 这是本方块唯一能把电量一次推过 2,147,483,647 的通路（有意设计）。
     */
    public long addEnergy(long amount) {
        if (!enabled || amount <= 0L) {
            return 0L;
        }
        long accepted = EnergyMath.accept(energy, amount);
        if (accepted > 0L) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        energy = Math.max(0L, data.getLong(NBT_ENERGY));
        // 老存档没有该键时按「开启」处理：新方块默认开启，不该因为缺键变关机。
        enabled = !data.contains(NBT_ENABLED) || data.getBoolean(NBT_ENABLED);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putLong(NBT_ENERGY, energy);
        data.putBoolean(NBT_ENABLED, enabled);
    }

    // ------------------------------------------------------------------
    // Capability
    // ------------------------------------------------------------------

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            boolean exposeDown = MatterReassemblerConfig.COMMON.exposeDownFace.get();
            if (EnergyMath.isEnergyFace(side, exposeDown)) {
                return energyCapability.cast();
            }
            return LazyOptional.empty();
        }
        // 物品能力等仍由 AE2 基类转发（它已把内部库存接成 IItemHandler），这里绝不能吞掉。
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }

    // ------------------------------------------------------------------
    // AE2 基类强制实现面
    // ------------------------------------------------------------------

    @Override
    public InternalInventory getInternalInventory() {
        // 本方块不提供物品栏：合成接管直接在网络与方块之间搬运，不需要本地槽位。
        return InternalInventory.empty();
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        // 无物品栏，恒不触发；留空实现只为满足基类契约。
    }

    @Override
    public void saveChanges() {
        setChanged();
    }

    // ------------------------------------------------------------------
    // IEnergyStorage 适配（int 边界，内部 long 运算）
    // ------------------------------------------------------------------

    private final class EnergyHandler implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            // 关闭态连模拟都返回 0：让外部机器立刻判定「抽/送不动」，而不是每刻重试拿到 0。
            if (!enabled || maxReceive <= 0) {
                return 0;
            }
            long accepted = EnergyMath.accept(energy, maxReceive);
            if (!simulate && accepted > 0L) {
                energy += accepted;
                setChanged();
            }
            return (int) accepted; // accepted ≤ maxReceive ≤ Integer.MAX_VALUE，转换安全
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!enabled || maxExtract <= 0) {
                return 0;
            }
            long extracted = EnergyMath.extract(energy, maxExtract);
            if (!simulate && extracted > 0L) {
                energy -= extracted;
                setChanged();
            }
            return (int) extracted;
        }

        @Override
        public int getEnergyStored() {
            // 对外永远是 int：内部可能远超 21 亿，钳制而非截断（截断会显示成负数）。
            return EnergyMath.toIntClamped(energy);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return enabled;
        }

        @Override
        public boolean canReceive() {
            return enabled;
        }
    }
}
