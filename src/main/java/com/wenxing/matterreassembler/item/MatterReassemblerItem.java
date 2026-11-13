package com.wenxing.matterreassembler.item;

import com.wenxing.matterreassembler.block.InfiniteMatterReassemblerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 物质重组器的物品形态：只加三段静态 tooltip（{@code DESIGN-1.21.1.md} §14 Wave 5 第 2 条）。
 *
 * <p>tooltip 里**不放实时电量**：物品不知道某个方块的池里有多少电，写死数字会变成谎言。
 * 实时数据由 Jade 与能量接口提供（§9.2）。
 *
 * <p>1.21.1 的签名差异：{@code appendHoverText} 的第二个参数从 {@code Level} 换成了
 * {@code Item.TooltipContext}（原版在 1.20.5 把「取世界的入口」收窄成上下文对象，
 * 以免 tooltip 在无世界场景下拿到 null）。本类不使用该参数，转发给父类即可。
 */
public class MatterReassemblerItem extends BlockItem {
    public MatterReassemblerItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.matterreassembler.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.matterreassembler.hint").withStyle(ChatFormatting.DARK_GRAY));
        // 二代机多一行：无限能源是它与一代机唯一的玩法差别，不写出来玩家分不清两台机器。
        if (getBlock() instanceof InfiniteMatterReassemblerBlock) {
            tooltip.add(Component.translatable("tooltip.matterreassembler.infinite").withStyle(ChatFormatting.AQUA));
        }
    }
}
