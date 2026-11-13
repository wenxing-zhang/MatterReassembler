package com.wenxing.matterreassembler.block;

import com.wenxing.matterreassembler.MatterReassembler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「物质重组器 2」的方块实体：在物质重组器之上自带无限能源。
 *
 * <p>与父类的唯一差别就是构造时把 {@code infiniteEnergy} 置为 true——所有能量路径
 * （取电永远够、外接充电照收、扣电无效）都由父类按这个标志分支，子类不再覆写任何行为。
 * 这样两代机器的语义不会分叉：父类改了能量逻辑，二代机自动跟上。
 */
public class InfiniteMatterReassemblerBlockEntity extends MatterReassemblerBlockEntity {

    public InfiniteMatterReassemblerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, true);
    }

    /**
     * 方块实体工厂入口（与父类同款：把取值推到运行期，避免字段初始化器自引用）。
     */
    public static InfiniteMatterReassemblerBlockEntity create(BlockPos pos, BlockState state) {
        return new InfiniteMatterReassemblerBlockEntity(
                MatterReassembler.MATTER_REASSEMBLER_2_BE.get(), pos, state);
    }
}
