package com.wenxing.matterreassembler.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import com.wenxing.matterreassembler.MatterReassembler;
import com.wenxing.matterreassembler.takeover.TakeoverService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 本项目<b>唯一</b>的 Mixin 注入点（{@code DESIGN-1.21.1.md} §6.2 设计裁定 D2 / §7.3 方案 D）。
 *
 * <p>注入 {@code CraftingService#submitJob} 的 HEAD 且可取消。为什么必须在这里短路、而不是
 * 把计划交回 CPU：空的 {@code patternTimes} 计划不会报错，只会让 CPU 任务<b>永久挂起</b>，
 * 材料滞留 CPU 内部库存直到玩家手动取消（§7 的完整证据链）。所以接管成功时本模组自行
 * 完成结算，并把原版提交短路成「成功、无链接」，CPU 完全不参与。
 *
 * <p>为什么这个签名安全：{@code submitJob} 的五个形参全是 AE2 自有类型
 * （{@code ICraftingPlan} / {@code ICraftingRequester} / {@code ICraftingCPU} / {@code boolean} /
 * {@code IActionSource}），AE2 的类名与方法名不进 SRG 映射，因此描述符跨小版本稳定
 * （AGENTS §3.1.1）。
 *
 * <p>线程：{@code submitJob} 由服务端线程调用，本注入不做任何跨线程动作；执行器内另有断言兜底。
 * <p><b>{@code remap = false} 是必须的，不是可选项</b>：AE2 是模组，它的类名与方法名不进 SRG 映射，
 * 运行期保持原名。Mixin 注解处理器默认会去 MC 的映射表里查 {@code CraftingService#submitJob}，
 * 查不到就报 {@code Unable to locate obfuscation mapping for @Inject target}（2026-11-11 实测编译失败）。
 * 关掉重映射后 AP 不再查表，注入目标就按字面名匹配——这与「描述符只依赖 AE2 自有类型」是同一个思路。
 */
@Mixin(value = CraftingService.class, priority = 1000, remap = false)
public abstract class CraftingServiceMixin {
    /**
     * {@code CraftingService} 的私有 {@code IGrid} 字段（javap 核对：{@code private final IGrid grid}）。
     * 通过它才能拿到「发起请求的那个网格」，进而用 {@code getMachines} 找本模组方块。
     */
    @Shadow
    @Final
    private IGrid grid;

    /**
     * 提交入口拦截。**任何异常都必须被吞下**：Mixin 里抛出的异常会击穿 AE2 的合成提交路径，
     * 把「本模组接管失败」放大成「玩家的合成功能坏掉」。失败一律走原版。
     */
    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, remap = false)
    private void matterreassembler$onSubmitJob(ICraftingPlan job,
                                               ICraftingRequester requestingMachine,
                                               ICraftingCPU target,
                                               boolean prioritizePower,
                                               IActionSource src,
                                               CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        try {
            if (TakeoverService.tryTakeOver(grid, job, requestingMachine, src)) {
                cir.setReturnValue(CraftingSubmitResult.successful(null));
                cir.cancel();
            }
        } catch (Throwable t) {
            MatterReassembler.LOGGER.warn("物质重组器接管判定异常，已交回 AE2 原版路径", t);
        }
    }
}
