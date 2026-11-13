package com.wenxing.matterreassembler;

import appeng.api.AECapabilities;
import com.mojang.logging.LogUtils;
import com.wenxing.matterreassembler.block.InfiniteMatterReassemblerBlock;
import com.wenxing.matterreassembler.block.InfiniteMatterReassemblerBlockEntity;
import com.wenxing.matterreassembler.block.MatterReassemblerBlock;
import com.wenxing.matterreassembler.block.MatterReassemblerBlockEntity;
import com.wenxing.matterreassembler.config.MatterReassemblerConfig;
import com.wenxing.matterreassembler.item.MatterReassemblerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * 物质重组器（Matter Reassembler）主类。
 * 目标环境：Minecraft 1.21.1 / NeoForge 21.1.x / AE2 19.2.x / Java 21。
 *
 * <p>本类只负责注册表接线与能力绑定。功能语义见 {@code DESIGN-1.21.1.md}，
 * 协作纪律见 {@code AGENTS.md}，变更流水见 {@code UPDATE.md}。
 *
 * <p>与 1.20.1（Forge）版的四处结构性差异：
 * <ol>
 *   <li>入口从「无参构造 + {@code FMLJavaModLoadingContext.get()}」改成「参数注入
 *       {@code IEventBus} 与 {@code ModContainer}」——NeoForge 不再提供全局静态上下文；</li>
 *   <li>{@code RegistryObject} 换成 {@code DeferredHolder} 族（{@code DeferredBlock}/{@code DeferredItem}），
 *       且方块/物品走 {@code DeferredRegister.Blocks/Items} 专用入口（注册方会补 {@code setId}）；</li>
 *   <li>能量 capability 从「方块实体覆写 {@code getCapability}」改成「注册期
 *       {@code RegisterCapabilitiesEvent} 绑定」——NeoForge 1.21 已删除覆写入口；</li>
 *   <li>配置注册从静态 {@code ModLoadingContext} 移到注入的 {@code ModContainer}。</li>
 * </ol>
 */
@Mod(MatterReassembler.MODID)
public final class MatterReassembler {
    /** 必须与 gradle.properties 的 mod_id、neoforge.mods.toml 的 modId 一致。 */
    public static final String MODID = "matterreassembler";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<MatterReassemblerBlock<MatterReassemblerBlockEntity>> MATTER_REASSEMBLER =
            BLOCKS.registerBlock("matter_reassembler",
                    properties -> new MatterReassemblerBlock<MatterReassemblerBlockEntity>(properties),
                    MatterReassemblerBlock.props());

    public static final DeferredItem<MatterReassemblerItem> MATTER_REASSEMBLER_ITEM =
            ITEMS.registerItem("matter_reassembler",
                    properties -> new MatterReassemblerItem(MATTER_REASSEMBLER.get(), properties));

    /** 二代机：外观与一代一致，方块实体自带无限能源。 */
    public static final DeferredBlock<InfiniteMatterReassemblerBlock> MATTER_REASSEMBLER_2 =
            BLOCKS.registerBlock("matter_reassembler_2",
                    InfiniteMatterReassemblerBlock::new,
                    MatterReassemblerBlock.props());

    public static final DeferredItem<MatterReassemblerItem> MATTER_REASSEMBLER_2_ITEM =
            ITEMS.registerItem("matter_reassembler_2",
                    properties -> new MatterReassemblerItem(MATTER_REASSEMBLER_2.get(), properties));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MatterReassemblerBlockEntity>> MATTER_REASSEMBLER_BE =
            BLOCK_ENTITIES.register("matter_reassembler", () -> BlockEntityType.Builder
                    // AE2 的方块实体构造器要 BlockEntityType 自身，而原版工厂只给 (pos, state)：
                    // 用静态工厂把取值推到运行期（工厂在区块加载时才被调用，那时注册已完成），
                    // 直接在本字段初始化器里写 MATTER_REASSEMBLER_BE.get() 会触发「初始化程序中存在自引用」。
                    .of(MatterReassemblerBlockEntity::create, MATTER_REASSEMBLER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfiniteMatterReassemblerBlockEntity>> MATTER_REASSEMBLER_2_BE =
            BLOCK_ENTITIES.register("matter_reassembler_2", () -> BlockEntityType.Builder
                    .of(InfiniteMatterReassemblerBlockEntity::create, MATTER_REASSEMBLER_2.get())
                    .build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> MATTER_REASSEMBLER_ITEM.get().getDefaultInstance())
                    .title(Component.translatable("itemGroup.matterreassembler.main"))
                    .displayItems((params, output) -> {
                        output.accept(MATTER_REASSEMBLER_ITEM.get());
                        output.accept(MATTER_REASSEMBLER_2_ITEM.get());
                    })
                    .build());

    public MatterReassembler(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(MatterReassembler::registerCapabilities);

        modContainer.registerConfig(ModConfig.Type.COMMON, MatterReassemblerConfig.COMMON_SPEC);
    }

    /**
     * 方块与方块实体的绑定必须在注册完成之后做（构造期拿不到 DeferredHolder 的实际值），
     * 所以放在公共初始化阶段。
     */
    @SuppressWarnings("unchecked")
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 必须带泛型参数强转而不能用 raw type：raw 调用会把 BlockEntityTicker<T> 擦成
            // BlockEntityTicker<BlockEntity>，参数位退化成 BlockEntity，
            // 于是 MatterReassemblerBlockEntity::serverTick 不再兼容（1.20.1 端实测编译报错）。
            ((MatterReassemblerBlock<MatterReassemblerBlockEntity>) MATTER_REASSEMBLER.get()).setBlockEntity(
                    MatterReassemblerBlockEntity.class,
                    MATTER_REASSEMBLER_BE.get(),
                    MatterReassemblerBlockEntity::serverTick,
                    null);
            // 二代机：方块实体自带无限能源。ticker 直接复用父类实现——
            // BlockEntityTicker<T> 的参数位是 T，方法引用接受父类型即可（参数逆变）。
            ((MatterReassemblerBlock<InfiniteMatterReassemblerBlockEntity>) MATTER_REASSEMBLER_2.get()).setBlockEntity(
                    InfiniteMatterReassemblerBlockEntity.class,
                    MATTER_REASSEMBLER_2_BE.get(),
                    MatterReassemblerBlockEntity::serverTick,
                    null);
        });
    }

    /**
     * 能量 capability 绑定。NeoForge 1.21 取消了方块实体的 {@code getCapability} 覆写，
     * 一律在注册期按「方块实体类型 + 面」声明；provider 返回 {@code null} 表示该面不暴露。
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MATTER_REASSEMBLER_BE.get(),
                (be, side) -> be.energyStorageFor(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MATTER_REASSEMBLER_2_BE.get(),
                (be, side) -> be.energyStorageFor(side));

        // AE2 19.x 靠这个 capability 发现「相邻方块是个网格节点宿主」。
        // 1.20.1 那边由 Forge 的 getCapability 桥接自动完成，19.x 改成注册期显式声明，
        // 第三方方块必须自己注册——漏掉的后果不是报错，而是**线缆根本看不见这个方块**：
        // 方块实体能建节点、Jade 也显示「已接入」，但线缆不会把它接进网络（2026-11-14 实机复现）。
        // provider 直接返回方块实体本身：IGridConnectedBlockEntity 继承自 IInWorldGridNodeHost。
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, MATTER_REASSEMBLER_BE.get(),
                (be, context) -> be);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, MATTER_REASSEMBLER_2_BE.get(),
                (be, context) -> be);
    }
}
