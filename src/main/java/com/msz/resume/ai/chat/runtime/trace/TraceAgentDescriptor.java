package com.msz.resume.ai.chat.runtime.trace;

/**
 * 运行时 Agent 展示描述。
 */
public record TraceAgentDescriptor(
        String agentId,
        String agentScope,
        String agentLabel,
        String subAgentType
) {

    public static TraceAgentDescriptor mainAgent() {
        return new TraceAgentDescriptor("main", "main", "Main Agent", null);
    }
}
