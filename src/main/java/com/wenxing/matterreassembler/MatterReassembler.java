package com.wenxing.matterreassembler;

import com.wenxing.matterreassembler.block.MatterReassemblerBlock;
import com.wenxing.matterreassembler.block.MatterReassemblerBlockEntity;
import com.wenxing.matterreassembler.config.MatterReassemblerConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 物质重组器（Matter Reassembler）主类。
 * 目标环境：Minecraft 1.20.1 / Forge 47.x / AE2 15.4.x / Java 17。
 *
 * <p>本类只负责注册表接线。功能语义见 DESIGN-1.20.1.md，协作纪律见 AGENTS.md，
 * 变更流水见 UPDATE.md。
 */
@Mod(MatterReassembler.MODID)
public final class MatterReassembler {
    /** 必须与 gradle.properties 的 mod_id、mods.toml 的 modId 一致。 */
    public static final String MODID = "matterreassembler";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> MATTER_REASSEMBLER =
            BLOCKS.register("matter_reassembler", MatterReassemblerBlock::new);

    public static final RegistryObject<Item> MATTER_REASSEMBLER_ITEM =
            ITEMS.register("matter_reassembler", () -> new BlockItem(MATTER_REASSEMBLER.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<MatterReassemblerBlockEntity>> MATTER_REASSEMBLER_BE =
            BLOCK_ENTITIES.register("matter_reassembler", () -> BlockEntityType.Builder
                    // AE2 的方块实体构造器要 BlockEntityType 自身，而 Forge 工厂只给 (pos, state)：
                    // 用静态工厂把取值推到运行期（工厂在区块加载时才被调用，那时注册已完成），
                    // 直接在本字段初始化器里写 MATTER_REASSEMBLER_BE.get() 会触发「初始化程序中存在自引用」。
                    .of(MatterReassemblerBlockEntity::create, MATTER_REASSEMBLER.get())
                    .build(null));

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
            .icon(() -> MATTER_REASSEMBLER_ITEM.get().getDefaultInstance())
            .title(Component.translatable("itemGroup.matterreassembler.main"))
            .displayItems((params, output) -> output.accept(MATTER_REASSEMBLER_ITEM.get()))
            .build());

    public MatterReassembler(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        CREATIVE_TABS.register(bus);
        bus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MatterReassemblerConfig.COMMON_SPEC);
    }

    /**
     * 方块与方块实体的绑定必须在注册完成之后做（构造期拿不到 RegistryObject 的实际值），
     * 所以放在公共初始化阶段。
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ((MatterReassemblerBlock) MATTER_REASSEMBLER.get()).setBlockEntity(
                MatterReassemblerBlockEntity.class,
                MATTER_REASSEMBLER_BE.get(),
                MatterReassemblerBlockEntity::serverTick,
                null));
    }
}
