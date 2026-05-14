package com.msz.resume.ai.chat.runtime.trace;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一管理运行步骤树的创建与状态变更。
 */
@Service
public class TraceService {

    public void startLlmRound(ChatRunTraceContext context, TraceAgentDescriptor agentDescriptor) {
        String stepId = context.nextStepId();
        context.rememberAgentRootStep(agentDescriptor.agentId(), stepId);
        if ("main".equals(agentDescriptor.agentScope())) {
            context.rememberMainLlmStep(stepId);
        }
        publish(context, agentDescriptor, StepTraceEvent.builder()
                .id(stepId)
                .runId(context.runId())
                .agentScope(agentDescriptor.agentScope())
                .agentId(agentDescriptor.agentId())
                .agentLabel(agentDescriptor.agentLabel())
                .kind("llm")
                .name("llm")
                .title(agentDescriptor.agentLabel())
                .op("started")
                .status("running")
                .timestamp(Instant.now())
                .meta(Map.of())
                .build());
    }

    public void completeMainLlmRound(ChatRunTraceContext context) {
        publishMainLlmRoundState(context, "completed", "success");
    }

    public void failMainLlmRound(ChatRunTraceContext context) {
        publishMainLlmRoundState(context, "failed", "failed");
    }

    public void recordContextRecall(ChatRunTraceContext context,
                                    TraceAgentDescriptor agentDescriptor,
                                    String op,
                                    String status,
                                    Map<String, Object> meta) {
        if (context == null || agentDescriptor == null) {
            return;
        }
        String stepId = context.nextStepId();
        String parentId = context.findAgentRootStep(agentDescriptor.agentId());
        publish(context, agentDescriptor, StepTraceEvent.builder()
                .id(stepId)
                .parentId(parentId)
                .runId(context.runId())
                .agentScope(agentDescriptor.agentScope())
                .agentId(agentDescriptor.agentId())
                .agentLabel(agentDescriptor.agentLabel())
                .kind("context_recall")
                .name("openviking_recall")
                .title("OpenViking 自动召回")
                .op(op)
                .status(status)
                .timestamp(Instant.now())
                .meta(meta != null ? meta : Map.of())
                .build());
    }

    private void publishMainLlmRoundState(ChatRunTraceContext context, String op, String status) {
        if (context == null || context.mainLlmStepId() == null) {
            return;
        }
        TraceAgentDescriptor mainAgent = TraceAgentDescriptor.mainAgent();
        publish(context, mainAgent, StepTraceEvent.builder()
                .id(context.mainLlmStepId())
                .runId(context.runId())
                .agentScope(mainAgent.agentScope())
                .agentId(mainAgent.agentId())
                .agentLabel(mainAgent.agentLabel())
                .kind("llm")
                .name("llm")
                .title(mainAgent.agentLabel())
                .op(op)
                .status(status)
                .timestamp(Instant.now())
                .meta(Map.of())
                .build());
    }

    public String startToolBatch(ChatRunTraceContext context,
                                 TraceAgentDescriptor agentDescriptor,
                                 List<ToolExecutionRequest> requests) {
        String stepId = context.nextStepId();
        String parentId = context.findAgentRootStep(agentDescriptor.agentId());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", requests != null ? requests.size() : 0);
        publish(context, agentDescriptor, StepTraceEvent.builder()
                .id(stepId)
                .parentId(parentId)
                .runId(context.runId())
                .agentScope(agentDescriptor.agentScope())
                .agentId(agentDescriptor.agentId())
                .agentLabel(agentDescriptor.agentLabel())
                .kind("tool_batch")
                .name("tool_batch")
                .title("批量工具调用")
                .op("started")
                .status("running")
                .timestamp(Instant.now())
                .meta(meta)
                .build());
        return stepId;
    }

    public String ensureToolBatch(ChatRunTraceContext context,
                                  TraceAgentDescriptor agentDescriptor,
                                  List<ToolExecutionRequest> requests) {
        if (context == null) {
            return null;
        }

        String batchStepId = findExistingToolBatch(context, requests);
        if (batchStepId == null || batchStepId.isBlank()) {
            batchStepId = startToolBatch(context, agentDescriptor, requests);
        }

        if (requests != null) {
            for (ToolExecutionRequest request : requests) {
                if (request == null) {
                    continue;
                }
                String toolStepId = context.findToolStep(request.id());
                if (toolStepId == null || toolStepId.isBlank()) {
                    planToolCall(context, agentDescriptor, batchStepId, request);
                }
            }
        }

        return batchStepId;
    }

    private String findExistingToolBatch(ChatRunTraceContext context, List<ToolExecutionRequest> requests) {
        if (context == null || requests == null) {
            return null;
        }
        for (ToolExecutionRequest request : requests) {
            if (request == null) {
                continue;
            }
            String toolStepId = context.findToolStep(toolCallKey(request));
            if (toolStepId == null || toolStepId.isBlank()) {
                continue;
            }
            String parentId = context.findStepParent(toolStepId);
            if (parentId != null && !parentId.isBlank()) {
                return parentId;
            }
        }
        return null;
    }

    public void completeToolBatch(ChatRunTraceContext context,
                                  TraceAgentDescriptor agentDescriptor,
                                  String batchStepId) {
        publishToolBatchState(context, agentDescriptor, batchStepId, "completed", "success");
    }

    public void failToolBatch(ChatRunTraceContext context,
                              TraceAgentDescriptor agentDescriptor,
                              String batchStepId) {
        publishToolBatchState(context, agentDescriptor, batchStepId, "failed", "failed");
    }

    public void pendingToolBatch(ChatRunTraceContext context,
                                 TraceAgentDescriptor agentDescriptor,
                                 String batchStepId) {
        publishToolBatchState(context, agentDescriptor, batchStepId, "pending", "pending");
    }

    private void publishToolBatchState(ChatRunTraceContext context,
                                       TraceAgentDescriptor agentDescriptor,
                                       String batchStepId,
                                       String op,
                                       String status) {
        if (context == null || batchStepId == null || batchStepId.isBlank()) {
            return;
        }
        publish(context, agentDescriptor, StepTraceEvent.builder()
                .id(batchStepId)
                .parentId(context.findAgentRootStep(agentDescriptor.agentId()))
                .runId(context.runId())
                .agentScope(agentDescriptor.agentScope())
                .agentId(agentDescriptor.agentId())
                .agentLabel(agentDescriptor.agentLabel())
                .kind("tool_batch")
                .name("tool_batch")
                .title("批量工具调用")
                .op(op)
                .status(status)
                .timestamp(Instant.now())
                .meta(Map.of())
                .build());
    }

    public String planToolCall(ChatRunTraceContext context,
                               TraceAgentDescriptor agentDescriptor,
                               String batchStepId,
                               ToolExecutionRequest request) {
        String stepId = context.nextStepId();
        String toolCallId = toolCallKey(request);
        context.rememberToolStep(toolCallId, stepId);
        context.rememberStepParent(stepId, batchStepId);
        publish(context, agentDescriptor, StepTraceEvent.builder()
                .id(stepId)
                .parentId(batchStepId)
                .runId(context.runId())
                .agentScope(agentDescriptor.agentScope())
                .agentId(agentDescriptor.agentId())
                .agentLabel(agentDescriptor.agentLabel())
                .kind("tool_call")
                .name(request.name())
                .title(request.name())
                .op("created")
                .status("running")
                .timestamp(Instant.now())
                .meta(buildToolMeta(request))
                .build());
        return stepId;
    }

    public void startToolCall(ChatRunTraceContext context,
                              TraceAgentDescriptor agentDescriptor,
                              ToolExecutionRequest request) {
        publishToolState(context, agentDescriptor, request, "started", "running");
    }

    public void completeToolCall(ChatRunTraceContext context,
                                 TraceAgentDescriptor agentDescriptor,
                                 ToolExecutionRequest request) {
        publishToolState(context, agentDescriptor, request, "completed", "success");
    }

    public void failToolCall(ChatRunTraceContext context,
                             TraceAgentDescriptor agentDescriptor,
                             ToolExecutionRequest request) {
        publishToolState(context, agentDescriptor, request, "failed", "failed");
    }

    public void blockToolCall(ChatRunTraceContext context,
                              TraceAgentDescriptor agentDescriptor,
                              ToolExecutionRequest request) {
        publishToolState(context, agentDescriptor, request, "blocked", "blocked");
    }

    public void pendingToolCall(ChatRunTraceContext context,
                                TraceAgentDescriptor agentDescriptor,
                                ToolExecutionRequest request) {
        publishToolState(context, agentDescriptor, request, "pending", "pending");
    }

    public TraceAgentDescriptor createSubAgentDescriptor(ChatRunTraceContext context, String subAgentType) {
        long sequence = context.nextSubAgentSequence();
        String typeLabel = (subAgentType != null && !subAgentType.isBlank()) ? subAgentType : "General";
        return new TraceAgentDescriptor(
                "sub_" + sequence,
                "sub",
                typeLabel + " #" + sequence,
                subAgentType
        );
    }

    public String startSubAgent(ChatRunTraceContext context,
                                TraceAgentDescriptor parentAgent,
                                ToolExecutionRequest request,
                                TraceAgentDescriptor subAgentDescriptor) {
        String stepId = context.nextStepId();
        String parentId = context.findToolStep(toolCallKey(request));
        context.rememberAgentRootStep(subAgentDescriptor.agentId(), stepId);
        context.rememberStepParent(stepId, parentId);
        publish(context, subAgentDescriptor, StepTraceEvent.builder()
                .id(stepId)
                .parentId(parentId)
                .runId(context.runId())
                .agentScope(subAgentDescriptor.agentScope())
                .agentId(subAgentDescriptor.agentId())
                .agentLabel(subAgentDescriptor.agentLabel())
                .kind("sub_agent")
                .name("spawnAgent")
                .title(subAgentDescriptor.agentLabel())
                .op("started")
                .status("running")
                .timestamp(Instant.now())
                .meta(Map.of(
                        "toolCallId", request.id(),
                        "subAgentType", subAgentDescriptor.subAgentType() != null ? subAgentDescriptor.subAgentType() : ""
                ))
                .build());
        return stepId;
    }

    public void completeSubAgent(ChatRunTraceContext context, TraceAgentDescriptor subAgentDescriptor, String subAgentStepId) {
        publish(context, subAgentDescriptor, StepTraceEvent.builder()
                .id(subAgentStepId)
                .parentId(context.findStepParent(subAgentStepId))
                .runId(context.runId())
                .agentScope(subAgentDescriptor.agentScope())
                .agentId(subAgentDescriptor.agentId())
                .agentLabel(subAgentDescriptor.agentLabel())
                .kind("sub_agent")
                .name("spawnAgent")
                .title(subAgentDescriptor.agentLabel())
                .op("completed")
                .status("success")
                .timestamp(Instant.now())
                .meta(Map.of("subAgentType", subAgentDescriptor.subAgentType() != null ? subAgentDescriptor.subAgentType() : ""))
                .build());
    }

    public void failSubAgent(ChatRunTraceContext context, TraceAgentDescriptor subAgentDescriptor, String subAgentStepId) {
        publish(context, subAgentDescriptor, StepTraceEvent.builder()
                .id(subAgentStepId)
                .parentId(context.findStepParent(subAgentStepId))
                .runId(context.runId())
                .agentScope(subAgentDescriptor.agentScope())
                .agentId(subAgentDescriptor.agentId())
                .agentLabel(subAgentDescriptor.agentLabel())
                .kind("sub_agent")
                .name("spawnAgent")
                .title(subAgentDescriptor.agentLabel())
                .op("failed")
                .status("failed")
                .timestamp(Instant.now())
                .meta(Map.of("subAgentType", subAgentDescriptor.subAgentType() != null ? subAgentDescriptor.subAgentType() : ""))
                .build());
    }

    private void publishToolState(ChatRunTraceContext context,
                                  TraceAgentDescriptor agentDescriptor,
                                  ToolExecutionRequest request,
                                  String op,
                                  String status) {
        String toolCallId = toolCallKey(request);
        String stepId = context.findToolStep(toolCallId);
        if (stepId == null || stepId.isBlank()) {
            stepId = context.nextStepId();
            context.rememberToolStep(toolCallId, stepId);
        }

        publish(context, agentDescriptor, StepTraceEvent.builder()
                .id(stepId)
                .parentId(context.findStepParent(stepId))
                .runId(context.runId())
                .agentScope(agentDescriptor.agentScope())
                .agentId(agentDescriptor.agentId())
                .agentLabel(agentDescriptor.agentLabel())
                .kind("tool_call")
                .name(request.name())
                .title(request.name())
                .op(op)
                .status(status)
                .timestamp(Instant.now())
                .meta(buildToolMeta(request))
                .build());
    }

    private Map<String, Object> buildToolMeta(ToolExecutionRequest request) {
        return Map.of(
                "toolCallId", request.id() != null ? request.id() : ""
        );
    }

    private String toolCallKey(ToolExecutionRequest request) {
        if (request != null && request.id() != null && !request.id().isBlank()) {
            return request.id();
        }
        return request != null
                ? request.name() + "_" + Integer.toHexString(System.identityHashCode(request))
                : "unknown_tool";
    }

    private void publish(ChatRunTraceContext context, TraceAgentDescriptor agentDescriptor, StepTraceEvent event) {
        if (context == null || event == null) {
            return;
        }
        TracePublisher publisher = context.publisher() != null
                ? context.publisher()
                : new SseTracePublisher(context);
        publisher.publishStep(event);
    }
}
