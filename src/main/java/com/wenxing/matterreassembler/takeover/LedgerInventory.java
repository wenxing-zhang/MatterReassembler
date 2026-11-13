package com.wenxing.matterreassembler.takeover;

/**
 * 收支账本用的最小库存接口——只保留「按 key 抽取 / 按 key 插入」两件事。
 *
 * <p>存在的理由（{@code DESIGN-1.21.1.md} §6.2 末段）：AE2 的 {@code MEStorage} 是运行期类型，
 * 直接依赖它会让账本无法被 T1 隔离探针覆盖。把库存抽象成这个两方法接口后，
 * 账本可以用纯数据（{@code Map<String, Long>} 之类的替身）跑到六条路径全覆盖，
 * 生产侧只留一个薄薄适配 {@code MEStorage} 的匿名实现。
 *
 * <p>{@code simulate = true} 时<b>不得</b>改变库存，且返回值必须是「假如执行能转移多少」。
 */
public interface LedgerInventory<K> {

    /** 从库存取出 {@code amount}，返回实际取出量（simulate 时返回「假若执行」的量）。 */
    long extract(K key, long amount, boolean simulate);

    /** 向库存放入 {@code amount}，返回实际放入量（simulate 时返回「假若执行」的量）。 */
    long insert(K key, long amount, boolean simulate);
}
