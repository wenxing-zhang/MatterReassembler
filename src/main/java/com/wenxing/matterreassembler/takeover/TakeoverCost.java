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

    /** 饱和加法：溢出时停在 {@link Long#MAX_VALUE}，绝不回绕成负数。 */
    public static long saturatingAdd(long a, long b) {
        if (a <= 0L) {
            return Math.max(0L, b);
        }
        if (b <= 0L) {
            return a;
        }
        if (a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    /**
     * 计划里最终产物的<b>实际投放量</b>。
     *
     * <p>为什么不能直接用 {@code plan.finalOutput().amount()}：那个字段是「玩家请求了多少」，
     * 不是「样板跑完会产出多少」。配方一次出 4 个而玩家只请求 1 个时，CPU 执行一次样板会产出
     * 4 个、全部入网；旁路若照请求量投放，玩家就只能拿到 1 个（2026-11-14 实测：1 个 vs 4 个）。
     *
     * @param producedByPattern 各样板「每次产出 × 执行次数」按物品聚合后的总量
     * @param key               最终产物的键
     * @param requested         玩家请求量（样板信息缺失时的回退值）
     * @param consumed          该键在 {@code usedItems} 里被当作材料消耗掉的数量（中间产物场景）
     */
    public static <K> long actualOutput(java.util.Map<K, Long> producedByPattern, K key,
                                        long requested, long consumed) {
        long produced = producedByPattern == null ? 0L : producedByPattern.getOrDefault(key, 0L);
        if (produced <= 0L) {
            // 拿不到样板明细时退回请求量：宁可少给，也不能凭空生成。
            return Math.max(0L, requested);
        }
        long net = produced - Math.max(0L, consumed);
        return Math.max(0L, net);
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
