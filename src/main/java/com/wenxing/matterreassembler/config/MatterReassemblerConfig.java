package com.wenxing.matterreassembler.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 配置项。默认值与 {@code DESIGN-1.20.1.md} §10 一致，改默认值必须同步更新记录。
 *
 * <p>键名一经发布不得重命名：Forge 会把配置写进玩家存档目录，改名等于静默重置玩家的设置。
 */
public final class MatterReassemblerConfig {
    /** 计费口径：按次（默认，一次接管固定成本）或按量（每产物单位）。 */
    public enum ChargeMode {
        PER_JOB,
        PER_UNIT
    }

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private MatterReassemblerConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue takeoverEnabled;
        public final ForgeConfigSpec.LongValue energyPerJob;
        public final ForgeConfigSpec.LongValue energyPerUnit;
        public final ForgeConfigSpec.EnumValue<ChargeMode> chargeMode;
        public final ForgeConfigSpec.BooleanValue exposeDownFace;
        public final ForgeConfigSpec.IntValue pollCoverInterval;
        public final ForgeConfigSpec.BooleanValue relaxCpuByteRequirement;
        public final ForgeConfigSpec.BooleanValue debugLogging;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("takeover");
            takeoverEnabled = builder
                    .comment("合成接管总开关。关闭后本方块只当储电方块，所有合成请求走 AE2 原版流程。")
                    .define("takeoverEnabled", true);
            energyPerJob = builder
                    .comment("按次计费：每次接管固定的 FE 成本（chargeMode=PER_JOB 时生效）。")
                    .defineInRange("energyPerJob", 100_000L, 0L, Long.MAX_VALUE);
            energyPerUnit = builder
                    .comment("按量计费：每个产物单位的 FE 成本（chargeMode=PER_UNIT 时生效）。")
                    .defineInRange("energyPerUnit", 100_000L, 0L, Long.MAX_VALUE);
            chargeMode = builder
                    .comment("计费口径。PER_JOB=按次（默认），PER_UNIT=按产物数量。")
                    .defineEnum("chargeMode", ChargeMode.PER_JOB);
            relaxCpuByteRequirement = builder
                    .comment("放宽确认界面的『所选 CPU 存储 ≥ 计划字节』前置（纯 UI 放宽，不影响结算）。")
                    .define("relaxCpuByteRequirement", false);
            builder.pop();

            builder.push("energy");
            exposeDownFace = builder
                    .comment("底面是否暴露 FE 接口。顶面永远是开关交互面，不暴露能量。")
                    .define("exposeDownFace", true);
            builder.pop();

            builder.push("compat");
            pollCoverInterval = builder
                    .comment("GTMThings 无线能源覆盖板的轮询间隔（刻）。0 = 只用覆盖板的被动推送。")
                    .defineInRange("pollCoverInterval", 20, 0, 1200);
            builder.pop();

            builder.push("debug");
            debugLogging = builder
                    .comment("打印合成接管判定的细节日志（debug 级）。")
                    .define("debugLogging", false);
            builder.pop();
        }
    }
}
