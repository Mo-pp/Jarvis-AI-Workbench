package com.msz.resume.ai.hook.impl;

import com.msz.resume.ai.hook.HookContext;
import com.msz.resume.ai.hook.HookResult;
import com.msz.resume.ai.hook.ToolHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 子Agent模式阻断 Hook
 *
 * <p>在子Agent模式下阻断特定的工具调用：
 * <ul>
 *   <li>askUserQuestion / askMultipleQuestions — 子Agent不能向用户提问</li>
 *   <li>spawnAgent — 子Agent不能再派发任务（防止递归嵌套）</li>
 * </ul>
 *
 * <p>此 Hook 从 ExecuteToolNode 的硬编码逻辑迁移而来。
 * 在 YAML 配置中通过正则匹配工具名：
 * <pre>
 * - name: sub_agent_block
 *   matcher: "^(askUserQuestion|askMultipleQuestions|spawnAgent)$"
 *   action: subAgentBlockHook
 *   priority: 10
 * </pre>
 *
 * <p>仅在 isSubAgent=true 时生效；非子Agent模式直接放行。
 */
@Slf4j
@Component("subAgentBlockHook")
public class SubAgentBlockHook implements ToolHook {

    @Override
    public HookResult preToolUse(HookContext context) {
        if (!context.isSubAgent()) {
            // 非子Agent模式，放行
            return HookResult.continueResult();
        }

        // 子Agent模式下，阻断 askUserQuestion / askMultipleQuestions / spawnAgent
        log.warn("[SubAgentBlockHook] 子Agent尝试调用 {}，已阻断", context.toolName());

        String blockMessage = switch (context.toolName()) {
            case "askUserQuestion", "askMultipleQuestions" ->
                    "错误：子Agent模式下不可向用户提问。请根据已有信息继续执行任务，或直接返回结果。";
            case "spawnAgent" ->
                    "错误：子Agent不可再派发任务。请直接执行当前任务。";
            default ->
                    "错误：子Agent模式下不可调用此工具。";
        };

        return HookResult.block(blockMessage);
    }

    @Override
    public String postToolUse(HookContext context, String toolResult) {
        // 子Agent阻断是 PreToolUse 行为，PostToolUse 无需处理
        return toolResult;
    }
}