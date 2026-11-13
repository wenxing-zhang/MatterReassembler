package com.wenxing.matterreassembler.compat.jade;

import com.wenxing.matterreassembler.MatterReassembler;
import com.wenxing.matterreassembler.block.MatterReassemblerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Jade 的<b>服务端</b>数据提供者（{@code DESIGN-1.21.1.md} §9.2）。
 *
 * <p>只放五个字段：{@code energy}(long) / {@code enabled} / {@code online} / {@code throughput}(int)
 * / {@code infinite}。<b>刻意不复用 {@code getUpdateTag}</b>：那会把 8 字节的 long 塞进每个玩家的
 * 块实体全量包，而 Jade 的数据通道只在准星指向时按需发送。
 *
 * <p>类加载隔离：本类只在 Jade 存在时被 Jade 的插件扫描器按类名加载（它的 {@code @WailaPlugin} 注解
 * 由 Jade 用 ASM 扫描，不需要预先加载本类）；未装 Jade 时本类在 jar 里但永不初始化，
 * 因而 {@code snownee.jade.api.*} 不会被解析——不需要额外的 {@code isLoaded} 门（AGENTS §5）。
 */
public final class EnergyDataProvider implements IServerDataProvider<BlockAccessor> {
    public static final String KEY_ENERGY = "energy";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_ONLINE = "online";
    public static final String KEY_THROUGHPUT = "throughput";
    public static final String KEY_INFINITE = "infinite";

    // 1.21 起 ResourceLocation 的公开构造器被移除，必须走静态工厂（否则编译不过）。
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MatterReassembler.MODID, "energy");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MatterReassemblerBlockEntity reassembler)) {
            // 方块被移除的瞬间可能拿到 null / 别的方块实体：什么都不写，客户端会显示 "-"。
            return;
        }
        // 服务端快照，客户端只负责格式化——数字不在客户端重新计算（DESIGN §9.3）。
        data.putLong(KEY_ENERGY, reassembler.getStoredEnergy());
        data.putBoolean(KEY_ENABLED, reassembler.isEnabled());
        data.putBoolean(KEY_ONLINE, reassembler.getMainNode().isActive());
        // 单次能量交互上限是 int 的硬约束（接口签名决定），不是配置项。
        data.putInt(KEY_THROUGHPUT, Integer.MAX_VALUE);
        // 无限能源（二代机）：客户端据此显示「无限」而不是一串天文数字。
        data.putBoolean(KEY_INFINITE, reassembler.isInfiniteEnergy());
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
