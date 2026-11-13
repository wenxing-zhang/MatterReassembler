package com.wenxing.matterreassembler.takeover;

/**
 * 接管资格判定（{@code DESIGN-1.21.1.md} §6.2 的六条条件）的<b>纯函数</b>实现。
 *
 * <p>只接收布尔与数值，不接触 {@code Level} / {@code IGrid} / {@code ICraftingPlan}——
 * 这样 T1 探针可以直接穷举每一条否决路径（{@code DESIGN} §11 断言 5）。
 * 真实世界状态的采集由 {@link TakeoverService} 负责，本类只做判定。
 */
public final class TakeoverDecision {
    private TakeoverDecision() {
    }

    /** 否决原因。{@link #OK} 表示可以接管。 */
    public enum Reason {
        OK,
        /** 配置总开关关闭。 */
        TAKEOVER_DISABLED,
        /** 请求来自机器 / 自动合成（{@code requestingMachine != null}），边界 B1。 */
        NOT_PLAYER_REQUEST,
        /** 计划是模拟（缺料），边界 B2。 */
        SIMULATION_PLAN,
        /** 计划自带缺失项（模拟标记与缺失项不一致时的兜底）。 */
        MISSING_ITEMS,
        /** 计划没有主产物（finalOutput 为空）：接管会扣掉材料却不投主产物，必须拦下。 */
        NO_FINAL_OUTPUT,
        /** 网格内没有「节点 active + 开关开启」的方块。 */
        NO_MACHINE,
        /** 电量不足以支付本次成本。 */
        ENERGY_TOO_LOW,
        /** 材料无法全部抽取。 */
        MATERIALS_UNAVAILABLE,
        /** 产物无法全部插入。 */
        OUTPUT_NO_SPACE
    }

    /**
     * 判定输入。字段全部由调用方采集，本类不做任何推导。
     *
     * @param eligibleMachines 网格内合格方块数（节点 active 且开关开启）
     * @param bestStoredEnergy 合格方块中电量最多者的存量
     * @param cost             本次接管的 FE 成本
     */
    public record Inputs(
            boolean globalEnabled,
            boolean playerRequest,
            boolean simulationPlan,
            boolean planHasMissingItems,
            boolean hasFinalOutput,
            int eligibleMachines,
            long bestStoredEnergy,
            long cost,
            boolean materialsExtractable,
            boolean outputFits) {
    }

    /**
     * 按「全局开关 → 请求来源 → 计划形态 → 方块 → 电量 → 材料 → 产物」的顺序判定，
     * 返回第一个不满足的原因（顺序即日志可读性：先报最根本的一条）。
     *
     * <p>材料与产物的检查在账本里以 simulate 完成，调用方先把结果填进 {@link Inputs}；
     * 这样判定与结算共享同一组事实，不会出现「判定通过、结算才发现不够」的分叉。
     */
    public static Reason check(Inputs in) {
        if (!in.globalEnabled()) {
            return Reason.TAKEOVER_DISABLED;
        }
        if (!in.playerRequest()) {
            return Reason.NOT_PLAYER_REQUEST;
        }
        if (in.simulationPlan()) {
            return Reason.SIMULATION_PLAN;
        }
        if (in.planHasMissingItems()) {
            return Reason.MISSING_ITEMS;
        }
        if (!in.hasFinalOutput()) {
            return Reason.NO_FINAL_OUTPUT;
        }
        if (in.eligibleMachines() <= 0) {
            return Reason.NO_MACHINE;
        }
        if (in.bestStoredEnergy() < in.cost()) {
            return Reason.ENERGY_TOO_LOW;
        }
        if (!in.materialsExtractable()) {
            return Reason.MATERIALS_UNAVAILABLE;
        }
        if (!in.outputFits()) {
            return Reason.OUTPUT_NO_SPACE;
        }
        return Reason.OK;
    }
}
