package com.wenxing.matterreassembler.block;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
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
 *
 * <p>1.21.1 的交互入口与 1.20.1 不同：AE2 19.x 跟随原版把方块交互拆成
 * {@code useItemOn}（手持物品）与 {@code useWithoutItem}（空手）两个方法。本方块只在
 * {@code useWithoutItem} 里接管——空手判定因此由引擎天然保证，比 1.20.1 手写
 * {@code heldItem.isEmpty()} 更严格：手持任何物品的交互根本不会走到这里。
 *
 * @param <T> 方块实体类型。物质重组器与「自带无限能源」的二代机共用本方块逻辑，
 *            只在方块实体上分家，所以这里必须泛型化——否则两代机器得复制整份方块代码。
 */
public class MatterReassemblerBlock<T extends MatterReassemblerBlockEntity> extends AEBaseEntityBlock<T> {
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public MatterReassemblerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ENABLED, Boolean.TRUE)
                .setValue(ONLINE, Boolean.FALSE));
    }

    /**
     * 方块属性。1.21.1 的 {@code DeferredRegister.Blocks#registerBlock} 要求属性由注册方提供
     * （它会在内部补 {@code setId}），所以属性不能再写死在构造器里。
     */
    public static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .noOcclusion();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENABLED, ONLINE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /**
     * 顶面开关：**空手 + 潜行 + 顶面** 三者同时满足才接管，其余一律 PASS。
     *
     * <p>空手由调用点保证（原版只在主手为空时才调 {@code useWithoutItem}），这里只剩
     * 「潜行 + 顶面」两条。刻意<b>不调 {@code super}</b>：AE2 的空手默认交互对本方块没有业务，
     * 而 1.20.1 端对 {@code onActivated} 的覆写同样没有转发——两版语义保持一致，
     * 其余组合一律 PASS，交回原版与其它模组（覆盖板、扳手等都在手里有物品的路径上）。
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || hit.getDirection() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MatterReassemblerBlockEntity reassembler) {
            reassembler.toggleActive();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
