package com.wenxing.matterreassembler;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * 物质重组器（Matter Reassembler）主类。
 * 目标环境：Minecraft 1.20.1 / Forge 47.x / AE2 15.4.x / Java 17。
 *
 * <p>本类只负责注册表接线。功能语义见 DESIGN-1.20.1.md，协作纪律见 AGENTS.md，
 * 变更流水见 UPDATE.md。
 *
 * <p>刻意保持零 AE2 引用：AE2 依赖接入（Wave 0 第 2 步）尚未完成，此处若引用 AE2 类型，
 * 工程会在依赖落地前连编译都过不去。
 */
@Mod(MatterReassembler.MODID)
public final class MatterReassembler {
    /** 必须与 gradle.properties 的 mod_id、mods.toml 的 modId 一致。 */
    public static final String MODID = "matterreassembler";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public MatterReassembler(FMLJavaModLoadingContext context) {
        var bus = context.getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
