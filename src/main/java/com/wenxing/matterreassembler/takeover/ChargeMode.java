package com.wenxing.matterreassembler.takeover;

/**
 * 计费口径。
 *
 * <p>刻意放在 {@code takeover} 包而不是配置类内部：配置类依赖 {@code ModConfigSpec}，
 * 而账本与计费必须保持「零加载器 / 零 MC 依赖」，否则 T1 隔离探针无法直接编译与运行
 * （见 {@code DESIGN-1.21.1.md} §11 断言 7）。
 *
 * <p>枚举常量名是配置文件里的持久值（`chargeMode=PER_JOB`），**不得重命名**。
 */
public enum ChargeMode {
    /** 按次：一次接管收固定成本，与请求数量无关。默认口径。 */
    PER_JOB,
    /** 按量：成本 = 每单位成本 × 产物单位数。 */
    PER_UNIT
}
