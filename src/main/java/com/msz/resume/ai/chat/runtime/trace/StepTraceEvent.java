package com.msz.resume.ai.chat.runtime.trace;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

/**
 * 统一的运行步骤事件。
 */
@Builder
public record StepTraceEvent(
        String id,
        String parentId,
        String runId,
        String agentScope,
        String agentId,
        String agentLabel,
        String kind,
        String name,
        String title,
        String op,
        String status,
        Instant timestamp,
        Map<String, Object> meta
) {
}
