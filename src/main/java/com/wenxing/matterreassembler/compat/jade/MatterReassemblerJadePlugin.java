package com.wenxing.matterreassembler.compat.jade;

import com.wenxing.matterreassembler.block.MatterReassemblerBlock;
import com.wenxing.matterreassembler.block.MatterReassemblerBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件入口（{@code DESIGN-1.21.1.md} §9.2）。
 *
 * <p>两次登记：
 * <ul>
 *   <li>服务端数据提供者，键 = 方块实体类；</li>
 *   <li>客户端组件提供者，键 = 方块类。</li>
 * </ul>
 *
 * <p>软依赖隔离：{@code @WailaPlugin} 由 Jade 自己的 ASM 扫描器发现，未装 Jade 时本类
 * <b>不会被任何入口加载</b>，主类常量池里也不出现 {@code snownee.jade.*}，因此不需要
 * {@code ModList.isLoaded} 门（AGENTS §5 软依赖红线）。
 *
 * <p>1.21.1 的 Jade 15.x 把客户端组件类型从 {@code IBlockComponentProvider} 收敛为
 * 泛型 {@code IComponentProvider<BlockAccessor>}，登记方法的形参类型随之改变；
 * 实现类因此改实现泛型接口（见 {@link EnergyComponentProvider}）。
 */
@WailaPlugin
public final class MatterReassemblerJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new EnergyDataProvider(), MatterReassemblerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new EnergyComponentProvider(), MatterReassemblerBlock.class);
    }
}
