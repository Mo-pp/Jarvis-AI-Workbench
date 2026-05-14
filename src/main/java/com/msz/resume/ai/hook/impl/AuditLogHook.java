package com.msz.resume.ai.hook.impl;

import com.msz.resume.ai.hook.HookContext;
import com.msz.resume.ai.hook.HookResult;
import com.msz.resume.ai.hook.ToolHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审计日志 Hook
 *
 * <p>记录所有工具调用的入参、结果、耗时，用于事后审查。
 * 使用独立的 Logger（{@code hook.audit}），不影响业务日志。
 *
 * <p>在 YAML 配置中注册为 PostToolUse Hook：
 * <pre>
 * post_tool_use:
 *   - name: audit_log
 *     matcher: ".*"
 *     action: auditLogHook
 *     priority: 100
 * </pre>
 */
@Slf4j
@Component("auditLogHook")
public class AuditLogHook implements ToolHook {

    /** 独立的审计日志 Logger，可在 logback 中单独配置输出目标 */
    private static final System.Logger AUDIT_LOG = System.getLogger("hook.audit");

    @Override
    public HookResult preToolUse(HookContext context) {
        // PreToolUse 只记录调用开始时间，不做阻断
        return HookResult.continueResult();
    }

    @Override
    public String postToolUse(HookContext context, String toolResult) {
        String truncatedArgs = truncate(context.arguments(), 500);
        String truncatedResult = truncate(toolResult, 1000);

        AUDIT_LOG.log(System.Logger.Level.INFO,
                "[AuditLog] session={}, tool={}, args={}, result={}",
                context.sessionId(), context.toolName(), truncatedArgs, truncatedResult);

        return toolResult;
    }

    /**
     * 截断长文本，避免日志膨胀
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated, total=" + text.length() + ")";
    }
}