package com.wenxing.matterreassembler.takeover;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.wenxing.matterreassembler.MatterReassembler;
import com.wenxing.matterreassembler.block.MatterReassemblerBlockEntity;
import com.wenxing.matterreassembler.config.MatterReassemblerConfig;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合成接管的执行器：把 AE2 的 {@link ICraftingPlan} 变成「扣料 + 投产物 + 扣 FE」。
 *
 * <p>被 {@code CraftingServiceMixin} 在 {@code submitJob} 入口调用。整条路径**只在服务端线程**
 * 展开（AE2 的 {@code submitJob} 本身就在服务端线程，本类另加断言兜底）：
 * 不解析 {@code Future}、不跨线程、不在 tick 里做任何事。
 *
 * <p>设计约束（{@code DESIGN-1.21.1.md} §6.2 / §7）：
 * <ul>
 *   <li>绝不把「空 {@code patternTimes}」的计划交回 CPU——那会让任务永久挂起（§7 证据链）；</li>
 *   <li>任何一步不满足都返回 {@code false} 且**不产生任何副作用**，由原版路径继续；</li>
 *   <li>扣电发生在材料与产物都已落地之后，失败路径一分钱不扣（§6.3）。</li>
 * </ul>
 */
public final class TakeoverService {
    private TakeoverService() {
    }

    /**
     * 尝试接管一次合成提交。
     *
     * @param grid             发起请求的网格（Mixin 从 {@code CraftingService} 的私有字段取得）
     * @param plan             已经算好的合成计划
     * @param requestingMachine 机器请求者为非 null（边界 B1：机器请求不接管）
     * @param src              发起者的 {@link IActionSource}，用于库存读写审计
     * @return true 表示已完成接管（调用方应短路为「提交成功、无链接」）
     */
    public static boolean tryTakeOver(IGrid grid, ICraftingPlan plan,
                                      ICraftingRequester requestingMachine, IActionSource src) {
        if (grid == null || plan == null) {
            return false;
        }

        MatterReassemblerConfig.Common cfg = MatterReassemblerConfig.COMMON;

        // 先取网格里所有本模组方块（不过滤），再筛合格项——
        // 出问题时「一台都没有」和「有但都不合格」是两种完全不同的故障，日志必须能区分。
        Set<MatterReassemblerBlockEntity> allMachines = grid.getMachines(MatterReassemblerBlockEntity.class);
        List<MatterReassemblerBlockEntity> eligible = collectEligible(allMachines);
        if (!eligible.isEmpty() && !isServerThread(eligible.get(0))) {
            // 埋点断言：本路径被设计为只在服务端线程运行。真走到这里说明 AE2 的调用线程语义变了，
            // 此时绝不能继续改世界数据，交回原版让 AE2 自己按它的规则处理。
            MatterReassembler.LOGGER.warn("物质重组器接管请求出现在非服务端线程，已交回 AE2 原版路径");
            return false;
        }
        // 多台合格方块时取电量最多的一台；同电量按坐标排序，保证选择确定（DESIGN §6.2）。
        eligible.sort(Comparator.comparingLong(MatterReassemblerBlockEntity::getStoredEnergy).reversed()
                .thenComparingLong(be -> be.getBlockPos().asLong()));

        GenericStack finalOutput = plan.finalOutput();
        long outputAmount = finalOutput != null ? finalOutput.amount() : 0L;
        int amountPerUnit = finalOutput != null ? finalOutput.what().getAmountPerUnit() : 1;
        long cost = TakeoverCost.totalCost(cfg.chargeMode.get(), cfg.energyPerJob.get(),
                cfg.energyPerUnit.get(), outputAmount, amountPerUnit);

        Map<AEKey, Long> materials = new LinkedHashMap<>();
        for (var entry : plan.usedItems()) {
            materials.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        Map<AEKey, Long> outputs = new LinkedHashMap<>();
        if (finalOutput != null) {
            outputs.merge(finalOutput.what(), finalOutput.amount(), Long::sum);
        }
        // 边界 B7：emittedItems（容器物 / 副产物）必须原样入网，否则容器类配方会吞桶吞瓶。
        for (var entry : plan.emittedItems()) {
            outputs.merge(entry.getKey(), entry.getValue(), Long::sum);
        }

        MEStorage storage = grid.getStorageService().getInventory();
        Ledger<AEKey> ledger = new Ledger<>(new LedgerInventory<AEKey>() {
            @Override
            public long extract(AEKey key, long amount, boolean simulate) {
                return storage.extract(key, amount, Actionable.ofSimulate(simulate), src);
            }

            @Override
            public long insert(AEKey key, long amount, boolean simulate) {
                return storage.insert(key, amount, Actionable.ofSimulate(simulate), src);
            }
        });

        Ledger.Preflight preflight = ledger.preflight(materials, outputs);
        long bestEnergy = eligible.isEmpty() ? 0L : eligible.get(0).getStoredEnergy();

        TakeoverDecision.Reason reason = TakeoverDecision.check(new TakeoverDecision.Inputs(
                cfg.takeoverEnabled.get(),
                requestingMachine == null,
                plan.simulation(),
                !plan.missingItems().isEmpty(),
                finalOutput != null,
                eligible.size(),
                bestEnergy,
                cost,
                preflight.materialsExtractable(),
                preflight.outputFits()));
        if (reason != TakeoverDecision.Reason.OK) {
            // 无条件打 info：合成提交是玩家操作级事件，频率极低不会刷屏；
            // 而「不接管」正是玩家最需要知道原因的时刻——藏进 debug 等于让人猜。
            // 无限能源版直接标「无限」：它的池存量是 Long.MAX_VALUE，
            // 打成一串 19 位数字会让日志读不出「这是二代机还是存了很多电的一代机」。
            String bestEnergyText = eligible.isEmpty() ? "0"
                    : (eligible.get(0).isInfiniteEnergy() ? "无限" : eligible.get(0).getStoredEnergy() + " FE");
            MatterReassembler.LOGGER.info(
                    "物质重组器不接管本次请求：{}（网格内方块 {} 台 / 合格 {} 台，最高电量 {}，本次成本 {} FE，"
                            + "材料可抽 {}，产物可放 {}，模拟计划 {}，机器请求 {}，计划有缺失项 {}）",
                    reason, allMachines.size(), eligible.size(), bestEnergyText, cost,
                    preflight.materialsExtractable(), preflight.outputFits(),
                    plan.simulation(), requestingMachine != null, !plan.missingItems().isEmpty());
            // 逐台明细降到 debug：网格里可能有很多台，一次不接管就是几十行 info，
            // 玩家在确认界面反复点会把日志迅速滚掉。摘要行（上面那条 info）已经带全判定事实，
            // 明细只在排查时开——debugLogging 打开或日志级别已调到 debug 时才遍历。
            if (cfg.debugLogging.get() || MatterReassembler.LOGGER.isDebugEnabled()) {
                for (MatterReassemblerBlockEntity be : allMachines) {
                    if (be == null) {
                        // 与 collectEligible 的口径保持一致：网格集合理论上不含 null，但不拿它赌。
                        continue;
                    }
                    MatterReassembler.LOGGER.debug("  - 方块 {} 开关={} 节点在线={} 无限能源={} 电量={}",
                            be.getBlockPos(), be.isEnabled(), be.getMainNode().isActive(), be.isInfiniteEnergy(),
                            be.isInfiniteEnergy() ? "无限" : be.getStoredEnergy() + " FE");
                }
            }
            return false;
        }

        MatterReassemblerBlockEntity target = eligible.get(0);
        Ledger.Result<AEKey> result = ledger.execute(materials, outputs);
        if (!result.success()) {
            // 回滚缺口非 0 意味着库存没能完全还原（网络在回滚瞬间被填满/被拆）。
            // 这不是可以静默吞掉的路径：material 已经动了，必须让服主在日志里看到。
            MatterReassembler.LOGGER.warn("物质重组器结算失败（{}，回滚缺口 {}），已交回 AE2 原版路径",
                    result.outcome(), result.rollbackShortfall());
            return false;
        }

        // 第 5 步：扣电。走到这里材料已扣、产物已入网，扣电不会失败（电量在判定阶段已核对）。
        target.setStoredEnergy(target.getStoredEnergy() - cost);

        if (cfg.debugLogging.get()) {
            MatterReassembler.LOGGER.debug("物质重组器接管成功：产物 {} × {}，扣电 {} FE，材料 {} 种，方块 {}",
                    finalOutput == null ? "-" : finalOutput.what(), outputAmount, cost,
                    result.extracted().size(), target.getBlockPos());
        }
        return true;
    }

    /** 从网格中已找到的本方块实体里筛出「节点 active + 开关开启 + 已加载」的部分。 */
    private static List<MatterReassemblerBlockEntity> collectEligible(Set<MatterReassemblerBlockEntity> machines) {
        List<MatterReassemblerBlockEntity> out = new ArrayList<>();
        for (MatterReassemblerBlockEntity be : machines) {
            if (be == null || be.isRemoved()) {
                continue;
            }
            Level level = be.getLevel();
            if (level == null || level.isClientSide) {
                continue;
            }
            if (!be.isEnabled()) {
                continue;
            }
            if (!be.getMainNode().isActive()) {
                continue;
            }
            out.add(be);
        }
        return out;
    }

    private static boolean isServerThread(MatterReassemblerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.getServer() == null) {
            return false;
        }
        return level.getServer().isSameThread();
    }
}
