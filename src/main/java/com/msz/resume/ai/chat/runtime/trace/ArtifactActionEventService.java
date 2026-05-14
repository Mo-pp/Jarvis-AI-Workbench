package com.msz.resume.ai.chat.runtime.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes user-visible artifact delivery events for the chat timeline.
 */
@Slf4j
@Service
public class ArtifactActionEventService {

    private final ObjectMapper objectMapper;
    private final TimelineActionService timelineActionService;

    public ArtifactActionEventService(ObjectMapper objectMapper,
                                      TimelineActionService timelineActionService) {
        this.objectMapper = objectMapper;
        this.timelineActionService = timelineActionService;
    }

    public void artifactReady(ChatRunTraceContext traceContext,
                              TraceAgentDescriptor agentDescriptor,
                              ToolExecutionRequest request,
                              String toolResult) {
        if (traceContext == null || !traceContext.isActive() || request == null || toolResult == null || toolResult.isBlank()) {
            return;
        }
        if (!"publishArtifact".equals(request.name())) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(toolResult);
            String type = root.path("type").asText("");
            if (type.isBlank() || "error".equals(type)) {
                return;
            }

            Map<String, Object> payload = timelineActionService
                    .builder("artifact_ready_" + (request.id() != null ? request.id() : Integer.toHexString(System.identityHashCode(request))),
                            traceContext,
                            agentDescriptor)
                    .toolCallId(request.id())
                    .title(titleFor(type))
                    .summary(summaryFor(type))
                    .status("success")
                    .put("artifactType", type)
                    .build();
            timelineActionService.publish(traceContext, "artifact_ready", payload, "ArtifactActionEventService");
        } catch (Exception e) {
            log.warn("[ArtifactActionEventService] artifact_ready send failed: toolCallId={}, error={}",
                    request.id(), e.getMessage());
        }
    }

    private String titleFor(String type) {
        return switch (type) {
            case "resume" -> "简历已生成";
            case "optimize_result" -> "优化分析已生成";
            case "mindmap" -> "思维导图已生成";
            case "markdown" -> "文档已生成";
            case "questionnaire" -> "问题清单已生成";
            default -> "产物已生成";
        };
    }

    private String summaryFor(String type) {
        return switch (type) {
            case "resume" -> "可在工作台预览、编辑和导出";
            case "optimize_result" -> "可在工作台查看匹配分析和优化建议";
            case "mindmap" -> "可在工作台打开查看结构图";
            case "markdown" -> "可在工作台打开查看内容";
            default -> "可在工作台打开查看";
        };
    }
}
