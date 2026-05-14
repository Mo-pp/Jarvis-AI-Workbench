package com.msz.resume.ai.chat.runtime.trace;

import lombok.extern.slf4j.Slf4j;
import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central boundary for user-visible chat timeline action payloads.
 */
@Slf4j
@Service
public class TimelineActionService {

    public static final String FIELD_PERSISTABLE = "persistable";
    public static final String FIELD_PROMPT_VISIBLE = "promptVisible";
    public static final String FIELD_SENSITIVE = "sensitive";

    public TimelineActionBuilder builder(String id,
                                         ChatRunTraceContext traceContext,
                                         TraceAgentDescriptor agentDescriptor) {
        return builder(id, traceContext, agentDescriptor, AgentDefaults.main());
    }

    public TimelineActionBuilder builder(String id,
                                         ChatRunTraceContext traceContext,
                                         TraceAgentDescriptor agentDescriptor,
                                         AgentDefaults defaults) {
        return new TimelineActionBuilder(id, traceContext, agentDescriptor, defaults != null ? defaults : AgentDefaults.main());
    }

    public Map<String, Object> pendingUserQuestionAction(String pendingId,
                                                         String pendingToolCallId,
                                                         List<?> pendingQuestions,
                                                         long sequence) {
        Map<String, Object> action = new LinkedHashMap<>();
        String safePendingId = pendingId != null ? pendingId : "";
        action.put("id", !safePendingId.isBlank()
                ? "user_question_" + safePendingId
                : "user_question_" + sequence);
        action.put("kind", "user_question");
        action.put("eventType", "pending");
        action.put("pendingId", safePendingId);
        action.put("toolCallId", pendingToolCallId != null ? pendingToolCallId : "");
        action.put("questions", pendingQuestions != null ? pendingQuestions : List.of());
        action.put("title", "需要你补充信息");
        action.put("summary", pendingQuestionSummary(pendingQuestions));
        action.put("questionCount", pendingQuestions != null && !pendingQuestions.isEmpty() ? pendingQuestions.size() : 1);
        action.put("status", "pending");
        action.put("firstSequence", sequence);
        action.put("sequence", sequence);
        action.put(FIELD_PROMPT_VISIBLE, false);
        action.put(FIELD_PERSISTABLE, true);
        action.put(FIELD_SENSITIVE, false);
        return action;
    }

    public void publish(ChatRunTraceContext traceContext,
                        String eventType,
                        Map<String, Object> payload,
                        String source) {
        if (traceContext == null || !traceContext.isActive() || traceContext.sink() == null) {
            return;
        }
        try {
            traceContext.sink().send(eventType, payload);
        } catch (Exception e) {
            log.warn("[TimelineActionService] SSE send failed: source={}, type={}, id={}, error={}",
                    source, eventType, payload != null ? payload.get("id") : null, e.getMessage());
        }
    }

    private String pendingQuestionSummary(List<?> pendingQuestions) {
        if (pendingQuestions == null || pendingQuestions.isEmpty()) {
            return "等待你的回答";
        }
        if (pendingQuestions.size() > 1) {
            return pendingQuestions.size() + " 个问题待回答";
        }
        Object first = pendingQuestions.getFirst();
        if (first instanceof QuestionDto question && question.getQuestionText() != null && !question.getQuestionText().isBlank()) {
            return question.getQuestionText();
        }
        if (first instanceof Map<?, ?> map) {
            Object questionText = map.get("questionText");
            if (questionText == null) {
                questionText = map.get("question");
            }
            if (questionText == null) {
                questionText = map.get("title");
            }
            if (questionText != null && !String.valueOf(questionText).isBlank()) {
                return String.valueOf(questionText);
            }
        }
        return "等待你的回答";
    }

    public record AgentDefaults(String agentScope, String agentId, String agentLabel) {

        public static AgentDefaults main() {
            return new AgentDefaults("main", "main", "Main Agent");
        }

        public static AgentDefaults subAgent() {
            return new AgentDefaults("sub", "", "Sub Agent");
        }
    }

    public static class TimelineActionBuilder {

        private final Map<String, Object> payload = new LinkedHashMap<>();

        private TimelineActionBuilder(String id,
                                      ChatRunTraceContext traceContext,
                                      TraceAgentDescriptor agentDescriptor,
                                      AgentDefaults defaults) {
            payload.put("id", stringOrBlank(id));
            payload.put("runId", traceContext != null ? stringOrBlank(traceContext.runId()) : "");
            payload.put("agentScope", firstNonBlank(
                    agentDescriptor != null ? agentDescriptor.agentScope() : "",
                    defaults.agentScope()
            ));
            payload.put("agentId", firstNonBlank(
                    agentDescriptor != null ? agentDescriptor.agentId() : "",
                    defaults.agentId()
            ));
            payload.put("agentLabel", firstNonBlank(
                    agentDescriptor != null ? agentDescriptor.agentLabel() : "",
                    defaults.agentLabel()
            ));
            payload.put("timestamp", Instant.now());
            payload.put(FIELD_PERSISTABLE, true);
            payload.put(FIELD_PROMPT_VISIBLE, false);
            payload.put(FIELD_SENSITIVE, false);
        }

        public TimelineActionBuilder toolCallId(String toolCallId) {
            payload.put("toolCallId", stringOrBlank(toolCallId));
            return this;
        }

        public TimelineActionBuilder title(String title) {
            payload.put("title", stringOrBlank(title));
            return this;
        }

        public TimelineActionBuilder status(String status) {
            payload.put("status", stringOrBlank(status));
            return this;
        }

        public TimelineActionBuilder summary(String summary) {
            payload.put("summary", stringOrBlank(summary));
            return this;
        }

        public TimelineActionBuilder error(String error) {
            payload.put("error", stringOrBlank(error));
            return this;
        }

        public TimelineActionBuilder persistable(boolean persistable) {
            payload.put(FIELD_PERSISTABLE, persistable);
            return this;
        }

        public TimelineActionBuilder promptVisible(boolean promptVisible) {
            payload.put(FIELD_PROMPT_VISIBLE, promptVisible);
            return this;
        }

        public TimelineActionBuilder sensitive(boolean sensitive) {
            payload.put(FIELD_SENSITIVE, sensitive);
            return this;
        }

        public TimelineActionBuilder put(String key, Object value) {
            if (key != null && !key.isBlank()) {
                payload.put(key, value);
            }
            return this;
        }

        public TimelineActionBuilder putAll(Map<String, Object> fields) {
            if (fields != null) {
                fields.forEach(this::put);
            }
            return this;
        }

        public Map<String, Object> build() {
            return new LinkedHashMap<>(payload);
        }

        private static String stringOrBlank(String value) {
            return value != null ? value : "";
        }

        private static String firstNonBlank(String first, String second) {
            return first != null && !first.isBlank() ? first : stringOrBlank(second);
        }
    }
}
