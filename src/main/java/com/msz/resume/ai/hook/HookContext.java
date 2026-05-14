package com.msz.resume.ai.hook;

import com.msz.resume.ai.chat.runtime.state.QueryLoopState;

/**
 * Hook 执行上下文
 *
 * <p>封装 Hook 执行时可访问的状态信息。
 * 提供给 Hook 实现的只读视图，避免 Hook 直接修改状态。
 *
 * @see HookService#preToolUse
 * @see HookService#postToolUse
 */
public record HookContext(

        /** 工具名称，如 "askUserQuestion"、"toolSearch" */
        String toolName,

        /** 工具调用请求的参数 JSON */
        String arguments,

        /** 当前 QueryLoopState（只读快照） */
        QueryLoopState state,

        /** 当前会话 ID */
        String sessionId,

        /** 当前工具调用 ID */
        String toolCallId,

        /** 是否为子Agent模式 */
        boolean isSubAgent
) {}