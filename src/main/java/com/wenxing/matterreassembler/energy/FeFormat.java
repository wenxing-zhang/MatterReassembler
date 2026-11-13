package com.wenxing.matterreassembler.energy;

import java.util.Locale;

/**
 * FE 数值格式化的<b>纯函数</b>（无 MC 依赖，T1 可覆盖）。
 *
 * <p>固定 {@link Locale#ROOT}：部分语言环境把千位分隔符换成空格或句点，
 * 而 Jade 与 tooltip 需要同一串字面（{@code DESIGN-1.21.1.md} §9.2）。
 * 负数一律按 0 显示——与 {@link EnergyMath#toIntClamped(long)} 的语义一致，
 * 负的存量在此前的设计里不存在，出现了也不该把它念给玩家看。
 */
public final class FeFormat {
    private FeFormat() {
    }

    /** {@code 1000000 → "1,000,000 FE"}；{@code 0 / 负数 → "0 FE"}。 */
    public static String format(long value) {
        long safe = Math.max(0L, value);
        return String.format(Locale.ROOT, "%,d FE", safe);
    }

    /**
     * 接口<b>单次调用上限</b>专用：{@code "2,147,483,647 FE/次"}。
     *
     * <p>不能写成 {@code FE/t}：这个数字是 {@code IEnergyStorage.extractEnergy(int, …)}
     * 的 int 参数天花板，<b>不是</b>每 tick 的实际速率——实际速率取决于对方每 tick 请求多少。
     * 写成「/t」会让玩家以为机器每 tick 能搬 21 亿。
     */
    public static String formatPerCall(long value) {
        long safe = Math.max(0L, value);
        return String.format(Locale.ROOT, "%,d FE/次", safe);
    }
}
