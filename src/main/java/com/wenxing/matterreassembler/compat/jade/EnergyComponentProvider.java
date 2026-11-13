package com.wenxing.matterreassembler.compat.jade;

import com.wenxing.matterreassembler.MatterReassembler;
import com.wenxing.matterreassembler.energy.FeFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 的<b>客户端</b>组件提供者：把服务端给的五个字段格式化成四行文本（{@code DESIGN} §9.1）。
 *
 * <p>客户端不做任何计算、不发任何网络请求：读 {@link BlockAccessor#getServerData()} → 格式化 → 追加行。
 * 数字格式化复用 {@link FeFormat}（固定 {@code Locale.ROOT}），保证 Jade 与 tooltip 的字面一致。
 *
 * <p>1.21.1 的 Jade 15.x 用泛型 {@link IComponentProvider}{@code <BlockAccessor>} 取代了
 * 原 {@code IBlockComponentProvider} 专型接口：登记方法的形参就是泛型接口，实现类必须跟上。
 */
public final class EnergyComponentProvider implements IComponentProvider<BlockAccessor> {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MatterReassembler.MODID, "energy");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(EnergyDataProvider.KEY_ENERGY)) {
            // 服务端数据缺失（方块刚被移除等）：显示 "-" 而不是 0，避免把「没数据」误读成「没电」。
            tooltip.add(Component.literal("-"));
            return;
        }

        boolean online = data.getBoolean(EnergyDataProvider.KEY_ONLINE);
        boolean enabled = data.getBoolean(EnergyDataProvider.KEY_ENABLED);

        tooltip.add(Component.translatable("jade.matterreassembler.network",
                Component.translatable(online
                        ? "jade.matterreassembler.online"
                        : "jade.matterreassembler.offline")));
        tooltip.add(Component.translatable("jade.matterreassembler.state",
                Component.translatable(enabled
                        ? "jade.matterreassembler.running"
                        : "jade.matterreassembler.disabled")));
        // 无限能源（二代机）：显示「无限」而不是一串天文数字，与「电量」同一行。
        if (data.getBoolean(EnergyDataProvider.KEY_INFINITE)) {
            tooltip.add(Component.translatable("jade.matterreassembler.energy",
                    Component.translatable("jade.matterreassembler.infinite")));
        } else {
            // 关闭态仍然显示电量：玩家需要知道停机时池里还剩多少（DESIGN §9.3）。
            tooltip.add(Component.translatable("jade.matterreassembler.energy",
                    FeFormat.format(data.getLong(EnergyDataProvider.KEY_ENERGY))));
        }
        tooltip.add(Component.translatable("jade.matterreassembler.throughput",
                FeFormat.formatPerCall(data.getInt(EnergyDataProvider.KEY_THROUGHPUT))));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
