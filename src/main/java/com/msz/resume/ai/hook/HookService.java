package com.msz.resume.ai.hook;

/**
 * Hook 拦截服务接口
 *
 * <p>在工具执行前后提供可插拔的拦截点，替代 ExecuteToolNode 中的硬编码 if-else 逻辑。
 *
 * <h2>拦截点</h2>
 * <ul>
 *   <li>{@link #preToolUse(HookContext)} — 工具执行前：可阻断、修改参数、放行</li>
 *   <li>{@link #postToolUse(HookContext, String)} — 工具执行后：可记录日志、修改结果</li>
 * </ul>
 *
 * <h2>执行链规则</h2>
 * <ul>
 *   <li>多个 Hook 按 priority 升序执行</li>
 *   <li>某个 Hook 返回 {@link HookResult#block(String)} 后，后续 Hook 和工具本身都不执行</li>
 *   <li>某个 Hook 返回 {@link HookResult#modifyArgs(java.util.Map)} 后，修改后的参数传递给下一个 Hook</li>
 *   <li>某个 Hook 抛异常时，捕获异常、记录日志、继续执行下一个 Hook</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // PreToolUse 拦截
 * HookResult result = hookService.preToolUse(ctx);
 * if (result.isBlocked()) {
 *     // 返回阻断信息给 LLM
 *     return ToolExecutionResultMessage.from(req, result.blockReason());
 * }
 *
 * // PostToolUse 回调
 * String finalResult = hookService.postToolUse(ctx, toolResult);
 * </pre>
 *
 * @see HookResult
 * @see HookContext
 */
public interface HookService {

    /**
     * 工具执行前拦截
     *
     * <p>在 ExecuteToolNode 执行工具之前调用。可返回：
     * <ul>
     *   <li>{@link HookResult#continueResult()} — 放行，继续执行</li>
     *   <li>{@link HookResult#block(String)} — 阻断，取消工具执行</li>
     *   <li>{@link HookResult#modifyArgs(java.util.Map)} — 修改工具入参后放行</li>
     * </ul>
     *
     * @param context Hook 执行上下文
     * @return Hook 执行结果
     */
    HookResult preToolUse(HookContext context);

    /**
     * 工具执行后回调
     *
     * <p>在 ExecuteToolNode 执行工具之后调用。可以记录日志、修改结果等。
     * 无论 preToolUse 是否放行，只要工具被执行了就会调用。
     *
     * @param context   Hook 执行上下文（与 preToolUse 相同）
     * @param toolResult 工具执行结果
     * @return 可能修改后的工具结果，或原结果
     */
    String postToolUse(HookContext context, String toolResult);
}