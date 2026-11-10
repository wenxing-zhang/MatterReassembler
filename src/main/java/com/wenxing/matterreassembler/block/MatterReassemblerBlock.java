package com.wenxing.matterreassembler.block;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 物质重组器方块。
 *
 * <p>外观完全由两个方块状态属性驱动（引擎负责同步，零自定义包）：
 * <ul>
 *   <li>{@link #ENABLED} 开关状态 → 亮/暗 + 红色指示灯；</li>
 *   <li>{@link #ONLINE} 网格节点是否 active → 白色框架 / 黑色框架。</li>
 * </ul>
 */
public class MatterReassemblerBlock extends AEBaseEntityBlock<MatterReassemblerBlockEntity> {
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public MatterReassemblerBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(ENABLED, Boolean.TRUE)
                .setValue(ONLINE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ENABLED, ONLINE);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return Shapes.block();
    }

    /**
     * 顶面开关：**空手 + 潜行 + 顶面** 三者同时满足才接管，其余一律 PASS。
     *
     * <p>空手判定是硬性要求：手持物品的 Shift+右键 属于其它模组的交互空间，
     * 本方块一旦吞掉它，玩家手里的工具/扳手/覆盖板就都失效了。
     * 潜行判定配合空手，进一步把「手持物品」「不潜行」两类误触排除在外。
     */
    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player,
                                        InteractionHand hand, ItemStack heldItem, BlockHitResult hit) {
        if (!heldItem.isEmpty() || !player.isShiftKeyDown() || hit.getDirection() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MatterReassemblerBlockEntity reassembler) {
            reassembler.toggleActive();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
