package com.wenxing.matterreassembler.config;

import com.wenxing.matterreassembler.takeover.ChargeMode;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 配置项。默认值与 {@code DESIGN-1.21.1.md} §10 一致，改默认值必须同步更新记录。
 *
 * <p>键名一经发布不得重命名：NeoForge 会把配置写进玩家存档目录，改名等于静默重置玩家的设置。
 *
 * <p><b>只放有消费者的键</b>。一个键如果没有代码读它，对玩家来说就是一个假开关。
 * 1.20.1 端曾清理掉两个悬空键（覆盖板轮询、确认界面放宽），本迁移版不带回。
 */
public final class MatterReassemblerConfig {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private MatterReassemblerConfig() {
    }

    public static final class Common {
        public final ModConfigSpec.BooleanValue takeoverEnabled;
        public final ModConfigSpec.LongValue energyPerJob;
        public final ModConfigSpec.LongValue energyPerUnit;
        public final ModConfigSpec.EnumValue<ChargeMode> chargeMode;
        public final ModConfigSpec.BooleanValue exposeDownFace;
        public final ModConfigSpec.BooleanValue debugLogging;

        Common(ModConfigSpec.Builder builder) {
            builder.push("takeover");
            takeoverEnabled = builder
                    .comment("合成接管总开关。关闭后本方块只当储电方块，所有合成请求走 AE2 原版流程。")
                    .define("takeoverEnabled", true);
            energyPerJob = builder
                    .comment("按次计费：每次接管固定的 FE 成本（chargeMode=PER_JOB 时生效）。默认 1 万，与物品描述一致。")
                    // 1.21.1 有两套 defineInRange：带 Class 的泛型版返回 ConfigValue，
                    // 不带 Class 的 long 专版返回 LongValue。这里要 LongValue，所以不传 Class。
                    .defineInRange("energyPerJob", 10_000L, 0L, Long.MAX_VALUE);
            energyPerUnit = builder
                    .comment("按量计费：每个产物单位的 FE 成本（chargeMode=PER_UNIT 时生效）。")
                    .defineInRange("energyPerUnit", 100_000L, 0L, Long.MAX_VALUE);
            chargeMode = builder
                    .comment("计费口径。PER_JOB=按次（默认），PER_UNIT=按产物数量。")
                    .defineEnum("chargeMode", ChargeMode.PER_JOB);
            builder.pop();

            builder.push("energy");
            exposeDownFace = builder
                    .comment("底面是否暴露 FE 接口。顶面永远是开关交互面，不暴露能量。")
                    .define("exposeDownFace", true);
            builder.pop();

            builder.push("debug");
            debugLogging = builder
                    .comment("打印合成接管成功路径的细节日志（debug 级；不接管的原因始终打 info）。")
                    .define("debugLogging", false);
            builder.pop();
        }
    }
}
