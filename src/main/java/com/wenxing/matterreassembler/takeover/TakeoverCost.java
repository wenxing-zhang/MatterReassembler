package com.wenxing.matterreassembler.takeover;

/**
 * 接管成本计算的<b>纯函数</b>集合（无 MC / Forge / AE2 依赖，T1 可直接覆盖）。
 *
 * <p>两条口径（{@code DESIGN-1.21.1.md} §6.3）：
 * <ul>
 *   <li>{@link ChargeMode#PER_JOB}：一次接管固定 {@code energyPerJob}，与数量无关——这是默认；
 *       「请求 1000 个与请求 1 个同价」是<b>有意</b>设计（AGENTS §8 已登记）。</li>
 *   <li>{@link ChargeMode#PER_UNIT}：{@code energyPerUnit × 单位数}。</li>
 * </ul>
 *
 * <p>单位数口径：可堆叠物品（{@code amountPerUnit == 1}）按个数；其它键类型（流体等）
 * 按 {@code getAmountPerUnit()} 归一，非整数<b>向上取整</b>——取整方向向下会让「不足一个单位」
 * 的请求免费，向上则最多多收不到一个单位的钱，方向选保守的一侧。
 */
public final class TakeoverCost {
    private TakeoverCost() {
    }

    /**
     * 产物对应的「单位数」。
     *
     * @param amount        产物数量（通常来自 {@code plan.finalOutput().amount()}）
     * @param amountPerUnit 该键类型的每单位含量（可堆叠物品为 1）
     */
    public static long unitsOf(long amount, int amountPerUnit) {
        if (amount <= 0L) {
            return 0L;
        }
        if (amountPerUnit <= 1) {
            return amount;
        }
        // 向上取整： (amount + apu - 1) / apu 在 amount 接近 Long.MAX_VALUE 时会溢出，
        // 改用除法取余的形式（amount / apu 与余数分开算）。
        long whole = amount / amountPerUnit;
        return amount % amountPerUnit == 0L ? whole : whole + 1L;
    }

    /** 成本乘法：溢出时饱和到 {@link Long#MAX_VALUE}，绝不回绕成负数（负成本会变成「白送电」）。 */
    public static long saturatingMultiply(long unitCost, long units) {
        if (unitCost <= 0L || units <= 0L) {
            return 0L;
        }
        if (units > Long.MAX_VALUE / unitCost) {
            return Long.MAX_VALUE;
        }
        return unitCost * units;
    }

    /** 本次接管的总 FE 成本。所有输入都来自配置与计划，不做任何世界访问。 */
    public static long totalCost(ChargeMode mode, long energyPerJob, long energyPerUnit,
                                 long amount, int amountPerUnit) {
        if (mode == ChargeMode.PER_UNIT) {
            return saturatingMultiply(energyPerUnit, unitsOf(amount, amountPerUnit));
        }
        // PER_JOB 与「未知口径」一律按次：默认口径是最保守的（不看数量）。
        return Math.max(0L, energyPerJob);
    }
}
