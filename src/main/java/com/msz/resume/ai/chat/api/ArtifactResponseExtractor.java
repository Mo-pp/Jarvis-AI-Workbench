package com.msz.resume.ai.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.api.dto.ChatArtifact;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从当前轮消息中提取前端工作台 artifact。
 *
 * <p>约束：只提取最新 UserMessage 之后产生的显式 artifact。普通 Markdown/正文不会被猜测为产物。
 */
@Slf4j
final class ArtifactResponseExtractor {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "mindmap",
            "questionnaire",
            "resume",
            "optimize_result",
            "markdown"
    );

    private static final Map<String, Set<String>> TOOL_ALLOWED_TYPES = Map.of(
            "generateMindmap", Set.of("mindmap"),
            "askUserQuestion", Set.of("questionnaire"),
            "askMultipleQuestions", Set.of("questionnaire"),
            "askQuestionnaire", Set.of("questionnaire")
    );

    private ArtifactResponseExtractor() {
    }

    static List<ChatArtifact> extractLatestArtifacts(List<ChatMessage> messages, ObjectMapper objectMapper) {
        if (messages == null || messages.isEmpty() || objectMapper == null) {
            return List.of();
        }

        List<ChatArtifact> artifacts = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof UserMessage) {
                break;
            }

            if (message instanceof ToolExecutionResultMessage toolMessage) {
                ChatArtifact artifact = artifactFromToolResult(toolMessage, objectMapper);
                if (artifact != null) {
                    artifacts.add(artifact);
                }
            } else if (message instanceof AiMessage aiMessage) {
                ChatArtifact artifact = artifactFromAssistantText(aiMessage.text(), objectMapper);
                if (artifact != null) {
                    artifacts.add(artifact);
                }
            }
        }

        Collections.reverse(artifacts);
        return artifacts;
    }

    static List<ChatArtifact> extractMessageArtifacts(ChatMessage message, ObjectMapper objectMapper) {
        if (message == null || objectMapper == null) {
            return List.of();
        }

        if (message instanceof ToolExecutionResultMessage toolMessage) {
            ChatArtifact artifact = artifactFromToolResult(toolMessage, objectMapper);
            return artifact != null ? List.of(artifact) : List.of();
        }

        if (message instanceof AiMessage aiMessage) {
            ChatArtifact artifact = artifactFromAssistantText(aiMessage.text(), objectMapper);
            return artifact != null ? List.of(artifact) : List.of();
        }

        return List.of();
    }

    static String extractLatestArtifactData(List<ChatArtifact> artifacts, String type, ObjectMapper objectMapper) {
        if (artifacts == null || artifacts.isEmpty() || type == null || objectMapper == null) {
            return null;
        }

        return artifacts.stream()
                .filter(artifact -> type.equals(artifact.getType()))
                .findFirst()
                .map(artifact -> toJson(artifact.getPayload(), objectMapper))
                .orElse(null);
    }

    static String stripPureArtifactText(String content, List<ChatArtifact> artifacts, ObjectMapper objectMapper) {
        if (content == null || content.isBlank() || artifacts == null || artifacts.isEmpty() || objectMapper == null) {
            return content;
        }

        JsonNode node = parseJsonObject(content, objectMapper);
        if (node == null || !node.isObject()) {
            return content;
        }

        String type = node.path("type").asText("");
        if (type.isBlank()) {
            return content;
        }

        boolean knownArtifact = artifacts.stream().anyMatch(artifact -> type.equals(artifact.getType()));
        return knownArtifact ? "" : content;
    }

    private static ChatArtifact artifactFromToolResult(ToolExecutionResultMessage toolMessage, ObjectMapper objectMapper) {
        String text = toolMessage.text();
        if (text == null || text.isBlank()) {
            return null;
        }

        JsonNode node = parseJsonObject(text, objectMapper);
        if (node == null) {
            return null;
        }

        String type = node.path("type").asText("");
        if (!isSupportedType(type)) {
            return null;
        }

        JsonNode artifactNode = unwrapPayloadNode(type, node);
        if (artifactNode == null) {
            return null;
        }

        Set<String> allowedTypes = TOOL_ALLOWED_TYPES.get(toolMessage.toolName());
        if (allowedTypes != null && !allowedTypes.contains(type)) {
            return null;
        }

        if (!isValidArtifactNode(type, artifactNode)) {
            return null;
        }

        return ChatArtifact.builder()
                .type(type)
                .payload(toPayload(artifactNode, objectMapper))
                .source("tool:" + toolMessage.toolName())
                .build();
    }

    private static ChatArtifact artifactFromAssistantText(String text, ObjectMapper objectMapper) {
        JsonNode node = parseJsonObject(text, objectMapper);
        if (node == null) {
            return null;
        }

        String type = node.path("type").asText("");
        if (!isSupportedType(type) || !isValidArtifactNode(type, node)) {
            return null;
        }

        return ChatArtifact.builder()
                .type(type)
                .payload(toPayload(node, objectMapper))
                .source("assistant")
                .build();
    }

    private static JsonNode parseJsonObject(String text, ObjectMapper objectMapper) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String trimmed = stripMarkdownFence(text.trim());
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(trimmed);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            log.debug("[ArtifactResponseExtractor] artifact JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String stripMarkdownFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }

        String normalized = text.replace("\r\n", "\n");
        int firstLineEnd = normalized.indexOf('\n');
        int lastFence = normalized.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return text;
        }
        return normalized.substring(firstLineEnd + 1, lastFence).trim();
    }

    private static boolean isSupportedType(String type) {
        return type != null && SUPPORTED_TYPES.contains(type);
    }

    private static JsonNode unwrapPayloadNode(String type, JsonNode node) {
        JsonNode payload = node.path("payload");
        if (payload.isMissingNode() || payload.isNull()) {
            return node;
        }

        // Structured tool results now return {"type":"...","payload":{...}}.
        // Resume-like artifacts still validate against the inner payload object.
        if ("resume".equals(type) || "optimize_result".equals(type)
                || "mindmap".equals(type) || "questionnaire".equals(type)
                || "markdown".equals(type)) {
            return payload;
        }
        return node;
    }

    private static boolean isValidArtifactNode(String type, JsonNode node) {
        return switch (type) {
            case "mindmap" -> node.path("markdown").isTextual() && !node.path("markdown").asText().isBlank();
            case "questionnaire" -> node.path("questions").isArray() && !node.path("questions").isEmpty();
            case "resume" -> node.path("resume").isObject() || hasResumeShape(node);
            case "optimize_result" -> node.has("matchScore") || node.path("matchAnalysis").isObject()
                    || node.path("suggestions").isArray() || node.path("highlights").isArray()
                    || node.path("optimizedResume").isObject() || node.path("resume").isObject();
            case "markdown" -> node.path("markdown").isTextual() && !node.path("markdown").asText().isBlank();
            default -> false;
        };
    }

    private static boolean hasResumeShape(JsonNode node) {
        return node.path("basicInfo").isObject()
                || node.path("jobIntention").isObject()
                || node.path("educationList").isArray()
                || node.path("workList").isArray()
                || node.path("projectList").isArray()
                || node.path("skillList").isArray();
    }

    private static Object toPayload(JsonNode node, ObjectMapper objectMapper) {
        return objectMapper.convertValue(
                node,
                objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)
        );
    }

    private static String toJson(Object payload, ObjectMapper objectMapper) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.debug("[ArtifactResponseExtractor] artifact 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
