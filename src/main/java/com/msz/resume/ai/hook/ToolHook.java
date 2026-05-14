package com.msz.resume.ai.hook;

/**
 * 单个 Hook 的执行接口
 *
 * <p>每个 Hook 实现此接口，并在 Spring 容器中注册为 Bean。
 * {@link HookEngine} 根据配置中的 {@code action} 字段查找对应的 Bean。
 *
 * <h2>命名约定</h2>
 * Bean 名称应与 YAML 配置中的 {@code action} 字段一致。
 * 例如 {@code action: askUserQuestionHook} → Bean 名称为 {@code "askUserQuestionHook"}。
 *
 * <h2>实现示例</h2>
 * <pre>
 * &#64;Component("askUserQuestionHook")
 * public class AskUserQuestionHook implements ToolHook {
 *     &#64;Override
 *     public HookResult preToolUse(HookContext context) {
 *         // 拦截逻辑...
 *         return HookResult.block("此工具需要等待用户输入");
 *     }
 *
 *     &#64;Override
 *     public String postToolUse(HookContext context, String toolResult) {
 *         return toolResult; // 一般不修改
 *     }
 * }
 * </pre>
 */
public interface ToolHook {

    /**
     * 工具执行前拦截
     *
     * @param context Hook 上下文
     * @return Hook 结果：放行 / 阻断 / 修改参数
     */
    HookResult preToolUse(HookContext context);

    /**
     * 工具执行后回调
     *
     * @param context    Hook 上下文
     * @param toolResult 工具执行结果
     * @return 可能修改后的工具结果
     */
    String postToolUse(HookContext context, String toolResult);
}