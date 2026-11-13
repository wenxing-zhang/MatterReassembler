package com.wenxing.matterreassembler.energy;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * 能量算术与面暴露判定的<b>纯函数</b>集合：不含世界、方块实体或线程依赖，
 * 因此可以被 T1 隔离探针直接覆盖（见 `.rivet/scratch/EnergyMathProbe.java`）。
 *
 * <p>存在的理由：对外 {@code IEnergyStorage} 只能返回 {@code int}，而内部电量池是 {@code long}。
 * 所有 long↔int 换算都收在这一个类里，避免散落的强制转换在某些路径上溢出成负数
 * （负数存量会让外部机器把它们当成"有电量"而反向抽取，是典型的静默失效）。
 */
public final class EnergyMath {
    private EnergyMath() {
    }

    /**
     * 内部 {@code long} → 对外 {@code int}：负数归 0，超出 {@link Integer#MAX_VALUE} 钳到上限。
     * 钳制而不是截断：截断会让 21 亿 + 1 显示成负数。
     */
    public static int toIntClamped(long value) {
        if (value <= 0L) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    /** 还能再装多少：用减法而不是加法，避免 {@code stored + requested} 溢出。 */
    public static long space(long stored) {
        if (stored <= 0L) {
            return Long.MAX_VALUE;
        }
        return Long.MAX_VALUE - stored;
    }

    /** 实际可接收量：负请求视为 0，且被剩余空间饱和。 */
    public static long accept(long stored, long requested) {
        if (requested <= 0L) {
            return 0L;
        }
        return Math.min(requested, space(stored));
    }

    /** 实际可抽取量：不超过存量，存量或请求为负时给 0。 */
    public static long extract(long stored, long requested) {
        if (requested <= 0L || stored <= 0L) {
            return 0L;
        }
        return Math.min(stored, requested);
    }

    /**
     * 能量面白名单：水平四面暴露；顶面永远不暴露（它是开关交互面）；
     * 底面按配置；{@code null}（内部访问）不暴露。
     */
    public static boolean isEnergyFace(@Nullable Direction side, boolean exposeDown) {
        if (side == null) {
            return false;
        }
        if (side == Direction.UP) {
            return false;
        }
        if (side == Direction.DOWN) {
            return exposeDown;
        }
        return true;
    }
}
