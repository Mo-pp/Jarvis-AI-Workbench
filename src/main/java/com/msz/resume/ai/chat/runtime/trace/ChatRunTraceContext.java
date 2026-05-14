package com.msz.resume.ai.chat.runtime.trace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单次 SSE 运行的 trace 上下文。
 */
public class ChatRunTraceContext {

    private final String runId;
    private final String sessionId;
    private final ChatStreamEventSink sink;
    private final TracePublisher publisher;
    private final AtomicLong stepSequence = new AtomicLong(0);
    private final AtomicLong subAgentSequence = new AtomicLong(0);
    private final Map<String, String> toolCallIdToStepId = new ConcurrentHashMap<>();
    private final Map<String, String> stepParentIndex = new ConcurrentHashMap<>();
    private final Map<String, String> agentIdToRootStepId = new ConcurrentHashMap<>();
    private volatile String mainLlmStepId;

    public ChatRunTraceContext(String runId, String sessionId, ChatStreamEventSink sink) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.sink = sink;
        this.publisher = null;
    }

    ChatRunTraceContext(String runId, String sessionId, TracePublisher publisher) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.sink = null;
        this.publisher = publisher;
    }

    public String runId() {
        return runId;
    }

    public String sessionId() {
        return sessionId;
    }

    public ChatStreamEventSink sink() {
        return sink;
    }

    TracePublisher publisher() {
        return publisher;
    }

    public boolean isActive() {
        return publisher != null || (sink != null && !sink.isClosed());
    }

    public String nextStepId() {
        return "step_" + stepSequence.incrementAndGet();
    }

    public String mainLlmStepId() {
        return mainLlmStepId;
    }

    public void rememberMainLlmStep(String stepId) {
        if (stepId != null && !stepId.isBlank()) {
            this.mainLlmStepId = stepId;
        }
    }

    public void rememberAgentRootStep(String agentId, String stepId) {
        if (agentId != null && !agentId.isBlank() && stepId != null && !stepId.isBlank()) {
            agentIdToRootStepId.put(agentId, stepId);
        }
    }

    public String findAgentRootStep(String agentId) {
        return agentId != null ? agentIdToRootStepId.get(agentId) : null;
    }

    public long nextSubAgentSequence() {
        return subAgentSequence.incrementAndGet();
    }

    public void rememberToolStep(String toolCallId, String stepId) {
        if (toolCallId != null && !toolCallId.isBlank() && stepId != null && !stepId.isBlank()) {
            toolCallIdToStepId.put(toolCallId, stepId);
        }
    }

    public String findToolStep(String toolCallId) {
        if (toolCallId == null || toolCallId.isBlank()) {
            return null;
        }
        return toolCallIdToStepId.get(toolCallId);
    }

    public void rememberStepParent(String stepId, String parentId) {
        if (stepId != null && !stepId.isBlank() && parentId != null && !parentId.isBlank()) {
            stepParentIndex.put(stepId, parentId);
        }
    }

    public String findStepParent(String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return null;
        }
        return stepParentIndex.get(stepId);
    }
}
