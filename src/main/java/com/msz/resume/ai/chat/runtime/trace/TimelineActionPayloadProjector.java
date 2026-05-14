package com.msz.resume.ai.chat.runtime.trace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Normalizes raw SSE event payloads into durable frontend timeline actions.
 */
public class TimelineActionPayloadProjector {

    public Optional<Map<String, Object>> project(String eventType, long sequence, Map<String, Object> payload) {
        if (!isTimelineEvent(eventType) || payload == null) {
            return Optional.empty();
        }

        Map<String, Object> actionPayload = normalizePayload(eventType, sequence, payload);
        String id = stringValue(actionPayload.get("id"));
        if (id.isBlank()) {
            id = eventType + "_" + sequence;
        }

        Map<String, Object> action = new LinkedHashMap<>(actionPayload);
        action.put("kind", kindFor(eventType));
        action.put("eventType", eventType);
        action.put("sequence", sequence);
        action.put("id", id);
        action.putIfAbsent("firstSequence", sequence);
        action.putIfAbsent(TimelineActionService.FIELD_PROMPT_VISIBLE, false);
        action.putIfAbsent(TimelineActionService.FIELD_PERSISTABLE, true);
        action.putIfAbsent(TimelineActionService.FIELD_SENSITIVE, false);
        return Optional.of(action);
    }

    public boolean isPersistable(Map<String, Object> action) {
        return action != null
                && booleanValue(action.getOrDefault(TimelineActionService.FIELD_PERSISTABLE, true))
                && !booleanValue(action.getOrDefault(TimelineActionService.FIELD_SENSITIVE, false));
    }

    public static boolean isTimelineEvent(String eventType) {
        return "assistant_checkpoint".equals(eventType)
                || "tool_use_started".equals(eventType)
                || "tool_use_delta".equals(eventType)
                || "tool_use_result".equals(eventType)
                || "tool_use_error".equals(eventType)
                || "artifact_ready".equals(eventType)
                || "delegation_started".equals(eventType)
                || "delegation_result".equals(eventType)
                || "delegation_error".equals(eventType)
                || "ask_user_question".equals(eventType)
                || "pending".equals(eventType);
    }

    public static String kindFor(String eventType) {
        return switch (eventType) {
            case "assistant_checkpoint" -> "checkpoint";
            case "artifact_ready" -> "artifact_ready";
            case "delegation_started", "delegation_result", "delegation_error" -> "delegation";
            case "ask_user_question", "pending" -> "user_question";
            default -> "tool_use";
        };
    }

    private static Map<String, Object> normalizePayload(String eventType, long sequence, Map<String, Object> payload) {
        if (!"ask_user_question".equals(eventType) && !"pending".equals(eventType)) {
            return payload;
        }

        Map<String, Object> action = new LinkedHashMap<>(payload);
        Object questions = payload.get("questions");
        int questionCount = questionCount(questions);
        String pendingId = stringValue(payload.get("pendingId"));
        String toolCallId = stringValue(payload.get("toolCallId"));
        String id = firstNonBlank(
                stringValue(payload.get("id")),
                pendingId.isBlank() ? "" : "user_question_" + pendingId,
                toolCallId.isBlank() ? "" : "user_question_" + toolCallId,
                "user_question_" + sequence
        );

        action.put("id", id);
        action.put("pendingId", pendingId);
        action.put("toolCallId", toolCallId);
        action.put("questions", questions);
        action.put(TimelineActionService.FIELD_PROMPT_VISIBLE, false);
        action.put(TimelineActionService.FIELD_PERSISTABLE, true);
        action.put(TimelineActionService.FIELD_SENSITIVE, false);
        action.put("title", firstNonBlank(stringValue(payload.get("title")), "需要你补充信息"));
        action.put("summary", firstNonBlank(stringValue(payload.get("summary")), questionSummary(questions, questionCount)));
        action.put("questionCount", questionCount);
        action.put("status", "pending");
        return action;
    }

    private static int questionCount(Object questions) {
        if (questions instanceof List<?> list) {
            return Math.max(1, list.size());
        }
        return 1;
    }

    private static String questionSummary(Object questions, int questionCount) {
        if (questionCount > 1) {
            return questionCount + " 个问题待回答";
        }
        if (questions instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof Map<?, ?> map) {
                String questionText = firstNonBlank(
                        stringValue(map.get("questionText")),
                        stringValue(map.get("question")),
                        stringValue(map.get("title"))
                );
                if (!questionText.isBlank()) {
                    return questionText;
                }
            }
        }
        return "等待你的回答";
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
