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
            "resume_evaluation",
            "resume_evaluation_pending",
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

        Set<String> artifactTypes = artifacts.stream()
                .map(ChatArtifact::getType)
                .filter(type -> type != null && !type.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (artifactTypes.isEmpty()) {
            return content;
        }

        String trimmed = stripMarkdownFence(content.trim());
        JsonNode wholeNode = parseJsonObject(content, objectMapper);
        if (wholeNode != null) {
            String type = wholeNode.path("type").asText("");
            if (artifactTypes.contains(type)) {
                return "";
            }
        }

        List<JsonObjectCandidate> candidates = findJsonObjectCandidates(trimmed, objectMapper).stream()
                .filter(candidate -> artifactTypes.contains(candidate.node().path("type").asText(""))
                        && isValidArtifactNode(candidate.node().path("type").asText(""), unwrapPayloadNode(candidate.node().path("type").asText(""), candidate.node())))
                .toList();
        if (candidates.isEmpty()) {
            return content;
        }

        StringBuilder sb = new StringBuilder(trimmed);
        for (int i = candidates.size() - 1; i >= 0; i--) {
            JsonObjectCandidate candidate = candidates.get(i);
            sb.delete(candidate.start(), candidate.endExclusive());
        }
        String cleaned = sb.toString()
                .replaceAll("(?is)```(?:json)?\\s*\\n\\s*```", "")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n\\s*\\n", "\n")
                .trim();
        return cleaned;
    }

    static String extractVisibleAssistantText(List<ChatMessage> messages,
                                              List<ChatArtifact> artifacts,
                                              ObjectMapper objectMapper) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        boolean hasArtifacts = artifacts != null && !artifacts.isEmpty();
        int latestUserIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                latestUserIndex = i;
                break;
            }
        }

        for (int i = messages.size() - 1; i > latestUserIndex; i--) {
            ChatMessage message = messages.get(i);
            if (!(message instanceof AiMessage aiMessage)) {
                continue;
            }

            String text = stripPureArtifactText(aiMessage.text(), artifacts, objectMapper);
            if (text == null || text.isBlank()) {
                continue;
            }
            if (hasArtifacts && aiMessage.hasToolExecutionRequests()) {
                continue;
            }
            return text;
        }

        return hasArtifacts ? "" : null;
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
            node = findJsonObjectCandidates(text, objectMapper).stream()
                    .map(JsonObjectCandidate::node)
                    .filter(candidate -> isSupportedType(candidate.path("type").asText("")))
                    .findFirst()
                    .orElse(null);
        }
        if (node == null) {
            return null;
        }

        String type = node.path("type").asText("");
        JsonNode artifactNode = unwrapPayloadNode(type, node);
        if (!isSupportedType(type) || artifactNode == null || !isValidArtifactNode(type, artifactNode)) {
            return null;
        }

        return ChatArtifact.builder()
                .type(type)
                .payload(toPayload(artifactNode, objectMapper))
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

    private static List<JsonObjectCandidate> findJsonObjectCandidates(String text, ObjectMapper objectMapper) {
        if (text == null || text.isBlank() || objectMapper == null) {
            return List.of();
        }

        List<JsonObjectCandidate> candidates = new ArrayList<>();
        int length = text.length();
        for (int start = 0; start < length; start++) {
            if (text.charAt(start) != '{') {
                continue;
            }

            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < length; i++) {
                char ch = text.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == '"') {
                        inString = false;
                    }
                    continue;
                }

                if (ch == '"') {
                    inString = true;
                } else if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        String raw = text.substring(start, i + 1);
                        try {
                            JsonNode node = objectMapper.readTree(raw);
                            if (node != null && node.isObject() && isSupportedType(node.path("type").asText(""))) {
                                candidates.add(new JsonObjectCandidate(start, i + 1, node));
                            }
                        } catch (Exception ignored) {
                            // Try the next balanced object.
                        }
                        start = i;
                        break;
                    }
                }
            }
        }
        return candidates;
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
                || "resume_evaluation".equals(type)
                || "resume_evaluation_pending".equals(type)
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
                    || node.path("optimizedResume").isObject() || node.path("resume").isObject()
                    || node.path("evaluation").isObject();
            case "resume_evaluation" -> node.path("quality").isObject()
                    || node.path("originalResume").isObject()
                    || node.path("generatedResume").isObject()
                    || node.path("jdMatch").isObject();
            case "resume_evaluation_pending" -> node.path("jobId").isTextual()
                    && !node.path("jobId").asText("").isBlank();
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

    private record JsonObjectCandidate(int start, int endExclusive, JsonNode node) {
    }
}
