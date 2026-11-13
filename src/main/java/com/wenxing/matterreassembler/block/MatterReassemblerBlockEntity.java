package com.wenxing.matterreassembler.block;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.wenxing.matterreassembler.MatterReassembler;
import com.wenxing.matterreassembler.config.MatterReassemblerConfig;
import com.wenxing.matterreassembler.energy.EnergyMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 物质重组器的方块实体。
 *
 * <p>职责边界：本类只负责「存电 + 开关 + 在线状态 + 把能量接口按面暴露」。
 * 合成接管的判定与结算在 {@code TakeoverService}，本类只提供判定所需的三个只读事实
 * （是否开启 / 电量 / 节点是否 active）。
 *
 * <p>1.21.1 的两个结构性差异（与 1.20.1 版对比）：
 * <ul>
 *   <li>基类换成 {@code AENetworkedBlockEntity}：AE2 19.x 把「网格连接」与「内部库存」
 *       拆成两个基类。本方块没有内部物品栏（合成接管直接在网络与方块之间搬运），
 *       因此用无库存的那个——不再需要实现 {@code getInternalInventory} 与
 *       {@code onChangeInventory} 两个空壳。</li>
 *   <li>能量不再经 {@code getCapability} 覆写暴露：NeoForge 1.21 取消了方块实体的
 *       capability 覆写入口，改由 {@code RegisterCapabilitiesEvent} 在注册期绑定。
 *       本类只留下纯函数式的 {@link #energyStorageFor(Direction)} 供事件里调用。</li>
 * </ul>
 *
 * <p>刻意不做的事：
 * <ul>
 *   <li>不在 tick 里主动拉/推能量——一切能量增减都必须由外部机器调用接口触发；</li>
 *   <li>不要求 AE 频道、不消耗 AE 电量，只借网格节点判断「是否已接入网络」。</li>
 * </ul>
 */
public class MatterReassemblerBlockEntity extends AENetworkedBlockEntity {
    public static final String NBT_ENERGY = "Energy";
    public static final String NBT_ENABLED = "Enabled";

    /** 在线状态复核间隔（刻）：换取零网络包，代价是外观最多延迟 0.5 秒跟上。 */
    private static final int ONLINE_RECHECK_INTERVAL = 10;

    /** 内部电量池。long 承载，对外只能经 {@link EnergyMath} 钳制成 int。 */
    private long energy;

    /**
     * 是否自带无限能源（二代机）。由构造器决定，**不进存档**：
     * 它是方块种类的属性，不是运行状态。
     */
    private final boolean infiniteEnergy;

    /** 开关状态：服务端权威，关闭时拒绝一切能量交互与合成接管。 */
    private boolean enabled = true;

    /** 单例适配器：capability 查询每面都返回同一个实例，它自己不做面判定（面判定在上层）。 */
    private final EnergyHandler energyHandler = new EnergyHandler();

    public MatterReassemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        this(type, pos, state, false);
    }

    /**
     * @param infiniteEnergy true 表示电量池视为无限（二代机）：取电永远够、外接充电照收不误、扣电无效。
     */
    protected MatterReassemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                           boolean infiniteEnergy) {
        super(type, pos, state);
        this.infiniteEnergy = infiniteEnergy;
        // 不占频道、不吃 AE 电量：它只是挂在网上读「是否在线」，不是用电器。
        getMainNode().setIdlePowerUsage(0.0);
    }

    /**
     * 方块实体工厂入口。
     *
     * <p>AE2 基类要求构造器拿到自己的 {@code BlockEntityType}，而原版工厂签名只有 {@code (pos, state)}，
     * 所以在这里从注册表取回来——此时注册早已完成（工厂只在区块加载时被调用）。
     * 这段取值不能写进 {@code MATTER_REASSEMBLER_BE} 的字段初始化器，那会构成「初始化程序自引用」。
     */
    public static MatterReassemblerBlockEntity create(BlockPos pos, BlockState state) {
        return new MatterReassemblerBlockEntity(MatterReassembler.MATTER_REASSEMBLER_BE.get(), pos, state);
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
    // 电量池（供能量接口与合成接管共用）
    // ------------------------------------------------------------------

    public long getStoredEnergy() {
        return infiniteEnergy ? Long.MAX_VALUE : energy;
    }

    /** 是否自带无限能源（Jade 用它显示「无限」而不是一串天文数字）。 */
    public boolean isInfiniteEnergy() {
        return infiniteEnergy;
    }

    /** 直接写池：仅用于存档读取与接管扣电，越界值收敛到 [0, Long.MAX_VALUE]。 */
    public void setStoredEnergy(long value) {
        if (infiniteEnergy) {
            // 无限池不接受写入：否则一次扣电就能把它写成一个有限值，隐藏的「用完」就出来了。
            return;
        }
        long clamped = Math.max(0L, value);
        if (energy != clamped) {
            energy = clamped;
            setChanged();
        }
    }

    /**
     * 追加电量并返回实际接收量。这条路**不受 int 上限约束**——
     * 它一次能推过 2,147,483,647（有意设计：接口上限是能力签名约束，不是池的容量）。
     */
    public long addEnergy(long amount) {
        if (!enabled || amount <= 0L) {
            return 0L;
        }
        if (infiniteEnergy) {
            // 无限池：照单全收（返回量表示「收下了」），但不改变任何状态。
            return amount;
        }
        long accepted = EnergyMath.accept(energy, amount);
        if (accepted > 0L) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        energy = Math.max(0L, data.getLong(NBT_ENERGY));
        // 老存档没有该键时按「开启」处理：新方块默认开启，不该因为缺键变关机。
        enabled = !data.contains(NBT_ENABLED) || data.getBoolean(NBT_ENABLED);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putLong(NBT_ENERGY, energy);
        data.putBoolean(NBT_ENABLED, enabled);
    }

    // 注意：**不覆写 saveChanges()**。1.21 的 AENetworkedBlockEntity 已从 AEBaseBlockEntity
    // 继承到非抽象实现，它除了标记「已变更」还会在 tick 末合并一次调用
    // （TickHandler.addCallable + setChangedQueued 去重）。自己写 setChanged() 会绕过那层合并，
    // 变成无谓的重复标记。1.20.1 那边基类是抽象成员才需要这个空壳，迁移时不该带过来。

    // ------------------------------------------------------------------
    // 能量 capability（由 RegisterCapabilitiesEvent 按面调用）
    // ------------------------------------------------------------------

    /**
     * 按面给出能量接口；返回 {@code null} 表示该面不暴露。
     *
     * <p>面白名单与 1.20.1 完全一致：水平四面暴露、顶面永不暴露（它是开关交互面）、
     * 底面按配置、{@code null}（内部访问）不暴露。判定本身是纯函数
     * （{@link EnergyMath#isEnergyFace(net.minecraft.core.Direction, boolean)}），T1 可覆盖。
     */
    @Nullable
    public IEnergyStorage energyStorageFor(@Nullable Direction side) {
        boolean exposeDown = MatterReassemblerConfig.COMMON.exposeDownFace.get();
        if (!EnergyMath.isEnergyFace(side, exposeDown)) {
            return null;
        }
        return energyHandler;
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
            if (infiniteEnergy) {
                return maxReceive;
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
            if (infiniteEnergy) {
                return maxExtract;
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
            if (infiniteEnergy) {
                return Integer.MAX_VALUE;
            }
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
