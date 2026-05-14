package com.msz.resume.ai.chat.runtime.trace;

import com.msz.resume.ai.chat.runtime.subagent.SubAgentResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes user-visible sub-agent delegation events for the chat timeline.
 */
@Service
public class DelegationActionEventService {

    private final TimelineActionService timelineActionService;

    public DelegationActionEventService(TimelineActionService timelineActionService) {
        this.timelineActionService = timelineActionService;
    }

    public void delegationStarted(ChatRunTraceContext traceContext,
                                  TraceAgentDescriptor subAgentDescriptor,
                                  ToolExecutionRequest request,
                                  String taskDescription) {
        publish(traceContext, "delegation_started", buildPayload(
                traceContext, subAgentDescriptor, request, taskDescription, "running", "委托任务已开始", null, null));
    }

    public void delegationSucceeded(ChatRunTraceContext traceContext,
                                    TraceAgentDescriptor subAgentDescriptor,
                                    ToolExecutionRequest request,
                                    String taskDescription,
                                    SubAgentResult result) {
        publish(traceContext, "delegation_result", buildPayload(
                traceContext,
                subAgentDescriptor,
                request,
                taskDescription,
                result != null && result.isMaxTurnsExceeded() ? "pending" : "success",
                resultSummary(result),
                null,
                result));
    }

    public void delegationFailed(ChatRunTraceContext traceContext,
                                 TraceAgentDescriptor subAgentDescriptor,
                                 ToolExecutionRequest request,
                                 String taskDescription,
                                 String error) {
        publish(traceContext, "delegation_error", buildPayload(
                traceContext, subAgentDescriptor, request, taskDescription, "failed", null, error, null));
    }

    private Map<String, Object> buildPayload(ChatRunTraceContext traceContext,
                                             TraceAgentDescriptor subAgentDescriptor,
                                             ToolExecutionRequest request,
                                             String taskDescription,
                                             String status,
                                             String summary,
                                             String error,
                                             SubAgentResult result) {
        String delegationId = delegationId(request);
        TimelineActionService.TimelineActionBuilder builder = timelineActionService
                .builder(delegationId, traceContext, subAgentDescriptor, TimelineActionService.AgentDefaults.subAgent())
                .toolCallId(request != null ? request.id() : null)
                .title(subAgentDescriptor != null ? "委托给 " + subAgentDescriptor.agentLabel() : "委托子 Agent")
                .status(status)
                .summary(summary)
                .error(error != null ? truncate(error, 220) : "")
                .put("agentType", subAgentDescriptor != null && subAgentDescriptor.subAgentType() != null ? subAgentDescriptor.subAgentType() : "")
                .put("task", taskDescription != null ? truncate(taskDescription, 220) : "");
        if (result != null) {
            builder.put("turnCount", result.turnCount())
                    .put("maxTurns", result.maxTurns())
                    .put("inputTokens", result.inputTokens())
                    .put("outputTokens", result.outputTokens());
        }
        return builder.build();
    }

    private void publish(ChatRunTraceContext traceContext, String type, Map<String, Object> payload) {
        timelineActionService.publish(traceContext, type, payload, "DelegationActionEventService");
    }

    private String delegationId(ToolExecutionRequest request) {
        if (request != null && request.id() != null && !request.id().isBlank()) {
            return "delegation_" + request.id();
        }
        return "delegation_" + Integer.toHexString(System.identityHashCode(request));
    }

    private String resultSummary(SubAgentResult result) {
        if (result == null) {
            return "子 Agent 已返回";
        }
        if (result.isMaxTurnsExceeded()) {
            return "子 Agent 已达到最大轮次，返回当前进展";
        }
        return "子 Agent 已完成，返回结果摘要";
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}
