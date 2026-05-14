package com.msz.resume.ai.chat.runtime.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineActionPayloadProjectorTest {

    private final TimelineActionPayloadProjector projector = new TimelineActionPayloadProjector();

    @Test
    @DisplayName("非 timeline 事件不会进入持久化投影")
    void shouldIgnoreNonTimelineEvent() {
        Optional<Map<String, Object>> action = projector.project(
                "message_delta",
                3L,
                Map.of("content", "partial")
        );

        assertTrue(action.isEmpty());
    }

    @Test
    @DisplayName("用户补充信息事件会归一化为稳定 action")
    void shouldNormalizePendingUserQuestionPayload() {
        Map<String, Object> action = projector.project(
                "pending",
                8L,
                Map.of(
                        "pendingId", "pending-1",
                        "toolCallId", "call-question",
                        "questions", List.of(Map.of("questionText", "你的目标岗位是什么？"))
                )
        ).orElseThrow();

        assertEquals("user_question_pending-1", action.get("id"));
        assertEquals("user_question", action.get("kind"));
        assertEquals("pending", action.get("eventType"));
        assertEquals("pending", action.get("status"));
        assertEquals(8L, action.get("sequence"));
        assertEquals(8L, action.get("firstSequence"));
        assertEquals(false, action.get("promptVisible"));
        assertEquals(true, action.get("persistable"));
        assertEquals(false, action.get("sensitive"));
        assertEquals("你的目标岗位是什么？", action.get("summary"));
    }

    @Test
    @DisplayName("显式 sensitive 事件不会被标记为可持久化")
    void shouldRejectSensitivePayloadForPersistence() {
        Map<String, Object> action = projector.project(
                "tool_use_result",
                5L,
                Map.of(
                        "id", "tool-1",
                        "title", "读取资料",
                        "status", "success",
                        "persistable", true,
                        "sensitive", true
                )
        ).orElseThrow();

        assertFalse(projector.isPersistable(action));
    }
}
