package com.wenxing.matterreassembler.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 「物质重组器 2」方块：外观、开关交互与物质重组器完全一致，差别只在方块实体自带无限能源。
 *
 * <p>继承泛型化的 {@link MatterReassemblerBlock} 而不是复制一份：两代机器的方块行为必须永远一致，
 * 复制一份就会在下次改交互时忘掉其中一个。
 *
 * <p>1.21.1 的注册入口要求方块构造器接受 {@code Properties}（注册方会在属性里补 {@code setId}），
 * 因此这里只做转发，属性仍由父类的 {@link MatterReassemblerBlock#props()} 统一提供。
 */
public class InfiniteMatterReassemblerBlock extends MatterReassemblerBlock<InfiniteMatterReassemblerBlockEntity> {
    public InfiniteMatterReassemblerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
