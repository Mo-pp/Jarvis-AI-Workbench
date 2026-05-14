package com.msz.resume.ai.chat.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChatStreamEvent 测试")
class ChatStreamEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("事件 envelope 应保存结构化 SSE 协议字段")
    void shouldKeepStructuredEnvelopeFields() {
        Instant timestamp = Instant.parse("2026-05-05T00:00:00Z");
        ChatStreamEvent event = ChatStreamEvent.builder()
                .type("message_done")
                .sessionId("session-1")
                .sequence(2)
                .timestamp(timestamp)
                .payload(Map.of(
                        "role", "assistant",
                        "content", "hello",
                        "streaming", false
                ))
                .build();

        assertEquals("message_done", event.getType());
        assertEquals("session-1", event.getSessionId());
        assertEquals(2, event.getSequence());
        assertEquals(timestamp, event.getTimestamp());
        assertEquals("assistant", event.getPayload().get("role"));
        assertEquals("hello", event.getPayload().get("content"));
        assertEquals(false, event.getPayload().get("streaming"));
    }

    @Test
    @DisplayName("打字机 delta 事件应只承载增量文本")
    void shouldKeepDeltaPayloadSmall() {
        ChatStreamEvent event = ChatStreamEvent.builder()
                .type("message_delta")
                .sessionId("session-1")
                .sequence(3)
                .timestamp(Instant.parse("2026-05-05T00:00:01Z"))
                .payload(Map.of(
                        "role", "assistant",
                        "delta", "hel"
                ))
                .build();

        assertEquals("message_delta", event.getType());
        assertEquals("assistant", event.getPayload().get("role"));
        assertEquals("hel", event.getPayload().get("delta"));
    }

    @Test
    @DisplayName("AskUserQuestion 事件应携带 pending 恢复所需字段")
    void shouldKeepAskUserQuestionPayloadFields() {
        ChatStreamEvent event = ChatStreamEvent.builder()
                .type("ask_user_question")
                .sessionId("session-1")
                .sequence(4)
                .timestamp(Instant.parse("2026-05-05T00:00:02Z"))
                .payload(Map.of(
                        "pendingId", "pending-1",
                        "toolCallId", "tool-1",
                        "questions", List.of(Map.of("questionId", "q1", "questionText", "继续吗？"))
                ))
                .build();

        assertEquals("ask_user_question", event.getType());
        assertEquals("pending-1", event.getPayload().get("pendingId"));
        assertEquals("tool-1", event.getPayload().get("toolCallId"));
        assertEquals(1, ((List<?>) event.getPayload().get("questions")).size());
    }

    @Test
    @DisplayName("事件应能正确序列化为 JSON（包含 Instant 时间戳）")
    void shouldSerializeToJsonCorrectly() throws Exception {
        Instant timestamp = Instant.parse("2026-05-05T00:00:00Z");
        ChatStreamEvent event = ChatStreamEvent.builder()
                .type("error")
                .sessionId("session-1")
                .sequence(1)
                .timestamp(timestamp)
                .payload(Map.of(
                        "code", "TEST_ERROR",
                        "message", "test error message",
                        "recoverable", false
                ))
                .build();

        String json = objectMapper.writeValueAsString(event);

        // 验证 JSON 包含所有必需字段
        assertTrue(json.contains("\"type\":\"error\""));
        assertTrue(json.contains("\"sessionId\":\"session-1\""));
        assertTrue(json.contains("\"sequence\":1"));
        assertTrue(json.contains("\"timestamp\":\"2026-05-05T00:00:00Z\""));  // ISO-8601 格式
        assertTrue(json.contains("\"code\":\"TEST_ERROR\""));
        assertTrue(json.contains("\"message\":\"test error message\""));
        assertTrue(json.contains("\"recoverable\":false"));
    }

    @Test
    @DisplayName("error 事件 payload 应能正确序列化")
    void shouldSerializeErrorPayloadCorrectly() throws Exception {
        ChatStreamEvent event = ChatStreamEvent.builder()
                .type("error")
                .sessionId("session-1")
                .sequence(1)
                .timestamp(Instant.now())
                .payload(Map.of(
                        "code", "STREAM_EXECUTION_ERROR",
                        "message", "执行异常",
                        "recoverable", false
                ))
                .build();

        String json = objectMapper.writeValueAsString(event);

        // 验证可以反序列化
        ChatStreamEvent deserialized = objectMapper.readValue(json, ChatStreamEvent.class);
        assertEquals("error", deserialized.getType());
        assertEquals("session-1", deserialized.getSessionId());
        assertEquals("STREAM_EXECUTION_ERROR", deserialized.getPayload().get("code"));
        assertEquals("执行异常", deserialized.getPayload().get("message"));
        assertEquals(false, deserialized.getPayload().get("recoverable"));
    }
}
