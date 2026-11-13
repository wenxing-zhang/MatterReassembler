package com.wenxing.matterreassembler.takeover;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合成接管的收支账本：严格按 {@code DESIGN-1.21.1.md} §6.2 的执行序 1→6 结算材料与产物。
 *
 * <p>只依赖 {@link LedgerInventory} 与 JDK 容器，因此 T1 探针可以覆盖全部失败路径
 * （材料不足 / 产物塞不下 / 抽料瞬间被抢 / 插产物瞬间被抢）。
 *
 * <p>不变量（任何一条被破坏都是 bug）：
 * <ol>
 *   <li>失败时网络状态必须与调用前等价——抽出的材料原样插回、插入的产物原样抽回；
 *       该等价性由 {@link Result#rollbackShortfall()} 量化暴露：非 0 表示回滚没做干净；</li>
 *   <li>{@link Outcome#SUCCESS} 之外的所有结果都不允许调用方扣电（{@code DESIGN} §6.3：
 *       「材料扣不动或产物投不下时一分钱不扣」）；</li>
 *   <li>成功时返回的 {@code extracted} / {@code inserted} 是<b>实际</b>转移量，供上层核对。</li>
 * </ol>
 */
public final class Ledger<K> {
    /** 账本结果。除 {@link #SUCCESS} 外，库存已被回滚到调用前状态（除非 rollbackShortfall 非 0）。 */
    public enum Outcome {
        SUCCESS,
        /** simulate 阶段判定材料不足：一次 modulate 都没发生。 */
        INSUFFICIENT_MATERIALS,
        /** simulate 阶段判定产物插不下：一次 modulate 都没发生。 */
        OUTPUT_NO_SPACE,
        /** simulate 通过但 modulate 抽料时被并发请求抢走：已抽部分已插回。 */
        MATERIALS_VANISHED,
        /** simulate 通过但 modulate 插产物时插不下：已插产物已抽回、已抽材料已插回。 */
        OUTPUT_RACE
    }

    /**
     * 结算结果。
     *
     * @param extracted         实际从网络抽走的材料（成功时才有意义）
     * @param inserted          实际插入网络的产物（成功时才有意义）
     * @param rollbackShortfall 回滚未能还原的数量合计。0 = 库存已完全恢复；
     *                          非 0 只可能出现在「回滚那一瞬间网络被填满 / 被拆掉」的极端情形，
     *                          调用方应记 warn 并人工核查——不能当作正常路径静默吞掉。
     */
    public record Result<T>(Outcome outcome, Map<T, Long> extracted, Map<T, Long> inserted, long rollbackShortfall) {
        public boolean success() {
            return outcome == Outcome.SUCCESS;
        }
    }

    private final LedgerInventory<K> inventory;

    public Ledger(LedgerInventory<K> inventory) {
        this.inventory = inventory;
    }

    /**
     * 只跑 simulate 的第 1、2 步，供<b>资格判定</b>使用。
     *
     * <p>单独抽出来的原因：判定与结算必须共享同一组事实。若判定自己算一遍、结算再算一遍，
     * 两处口径一旦漂移就会出现「判定说能、结算才发现不能」的分叉，玩家看到的是点击后无反应。
     */
    public Preflight preflight(Map<K, Long> materials, Map<K, Long> outputs) {
        Map<K, Long> demandMaterials = normalize(materials);
        Map<K, Long> demandOutputs = normalize(outputs);
        boolean materialsOk = true;
        for (Map.Entry<K, Long> e : demandMaterials.entrySet()) {
            if (inventory.extract(e.getKey(), e.getValue(), true) < e.getValue()) {
                materialsOk = false;
                break;
            }
        }
        boolean outputsOk = true;
        if (materialsOk) {
            for (Map.Entry<K, Long> e : insertionDemand(demandMaterials, demandOutputs).entrySet()) {
                if (inventory.insert(e.getKey(), e.getValue(), true) < e.getValue()) {
                    outputsOk = false;
                    break;
                }
            }
        }
        return new Preflight(materialsOk, outputsOk);
    }

    /** simulate 结论：材料能否全部抽出、产物能否全部插入。 */
    public record Preflight(boolean materialsExtractable, boolean outputFits) {
    }

    /**
     * 执行一次完整结算。
     *
     * @param materials 需要从网络抽取的材料（key → 数量）
     * @param outputs   需要插入网络的产物，含副产物 / 容器物（key → 数量，边界 B7）
     */
    public Result<K> execute(Map<K, Long> materials, Map<K, Long> outputs) {
        Map<K, Long> demandMaterials = normalize(materials);
        Map<K, Long> demandOutputs = normalize(outputs);

        // ---- 第 1 步：simulate 抽取材料，全通过才继续 ----
        for (Map.Entry<K, Long> e : demandMaterials.entrySet()) {
            if (inventory.extract(e.getKey(), e.getValue(), true) < e.getValue()) {
                return failure(Outcome.INSUFFICIENT_MATERIALS, 0L);
            }
        }

        // ---- 第 2 步：simulate 插入产物 ----
        // 同一 key 既被抽又被插时，抽走的那部分必然腾出等量空间，所以插入位需求取净额。
        // 用净额而不是全额只是减少误判（更少地退回原版），不影响正确性：
        // 真正插入时仍按全额进行，若插不下会在第 4 步回滚。
        for (Map.Entry<K, Long> e : insertionDemand(demandMaterials, demandOutputs).entrySet()) {
            if (inventory.insert(e.getKey(), e.getValue(), true) < e.getValue()) {
                return failure(Outcome.OUTPUT_NO_SPACE, 0L);
            }
        }

        // ---- 第 3 步：modulate 抽取材料（记录实际量，便于回滚与上层核对）----
        Map<K, Long> extracted = new LinkedHashMap<>();
        for (Map.Entry<K, Long> e : demandMaterials.entrySet()) {
            long got = inventory.extract(e.getKey(), e.getValue(), false);
            if (got > 0L) {
                extracted.merge(e.getKey(), got, Long::sum);
            }
            // simulate 通过而 modulate 不足，只能是并发请求抢走了同一批物料。
            // 此时绝不能继续插产物（那会凭空生成材料），先回滚已抽部分。
            if (got < e.getValue()) {
                return failure(Outcome.MATERIALS_VANISHED, restore(extracted));
            }
        }

        // ---- 第 4 步：modulate 插入产物；不足则整体回滚 ----
        Map<K, Long> inserted = new LinkedHashMap<>();
        for (Map.Entry<K, Long> e : demandOutputs.entrySet()) {
            long put = inventory.insert(e.getKey(), e.getValue(), false);
            if (put > 0L) {
                inserted.merge(e.getKey(), put, Long::sum);
            }
            if (put < e.getValue()) {
                long shortfall = uninsert(inserted) + restore(extracted);
                return failure(Outcome.OUTPUT_RACE, shortfall);
            }
        }

        // ---- 第 5 步（扣电）由调用方完成，第 6 步（返回成功）由 Mixin 完成 ----
        return new Result<>(Outcome.SUCCESS, extracted, inserted, 0L);
    }

    /** 把已抽出的材料原样插回网络，返回未能插回的量。 */
    private long restore(Map<K, Long> extracted) {
        long shortfall = 0L;
        for (Map.Entry<K, Long> e : extracted.entrySet()) {
            long back = inventory.insert(e.getKey(), e.getValue(), false);
            if (back < e.getValue()) {
                shortfall += e.getValue() - back;
            }
        }
        return shortfall;
    }

    /** 把已插入的产物抽回，返回未能抽回的量（抽不回等于产物被复制了一份，必须上报）。 */
    private long uninsert(Map<K, Long> inserted) {
        long shortfall = 0L;
        for (Map.Entry<K, Long> e : inserted.entrySet()) {
            long back = inventory.extract(e.getKey(), e.getValue(), false);
            if (back < e.getValue()) {
                shortfall += e.getValue() - back;
            }
        }
        return shortfall;
    }

    private static <K> Map<K, Long> insertionDemand(Map<K, Long> materials, Map<K, Long> outputs) {
        Map<K, Long> need = new LinkedHashMap<>(outputs);
        for (Map.Entry<K, Long> e : materials.entrySet()) {
            Long out = need.get(e.getKey());
            if (out != null) {
                long net = out - e.getValue();
                if (net <= 0L) {
                    need.remove(e.getKey());
                } else {
                    need.put(e.getKey(), net);
                }
            }
        }
        return need;
    }

    /** 去掉空条目并合并重复 key，避免出现两层循环里对同一 key 的两次判定。 */
    private static <K> Map<K, Long> normalize(Map<K, Long> in) {
        Map<K, Long> out = new LinkedHashMap<>();
        if (in == null) {
            return out;
        }
        for (Map.Entry<K, Long> e : in.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0L) {
                out.merge(e.getKey(), e.getValue(), Long::sum);
            }
        }
        return out;
    }

    private static <T> Result<T> failure(Outcome outcome, long shortfall) {
        return new Result<>(outcome, Map.of(), Map.of(), shortfall);
    }
}
