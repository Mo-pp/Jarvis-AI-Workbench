package com.msz.resume.ai.chat.runtime.trace.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.entity.TimelineActionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineActionRecordProjectorTest {

    private final TimelineActionRecordProjector projector = new TimelineActionRecordProjector(new ObjectMapper());

    @Test
    @DisplayName("Trace stream event 会投影为 timeline action 持久化记录")
    void shouldProjectTraceStreamEventToRecord() {
        TraceStreamEvent event = new TraceStreamEvent(
                "1747137600000-0",
                "session-1:run-1:5:tool-1",
                "session-1",
                "run-1",
                "tool_use_result",
                "tool-1",
                5L,
                3L,
                -1,
                Map.of(
                        "id", "tool-1",
                        "eventType", "tool_use_result",
                        "kind", "tool_use",
                        "status", "success",
                        "title", "读取资源",
                        "persistable", true,
                        "promptVisible", false
                ),
                Instant.parse("2026-05-13T08:00:00Z")
        );

        TimelineActionRecord record = projector.project(event).orElseThrow();

        assertEquals("session-1", record.getSessionId());
        assertEquals("tool-1", record.getActionId());
        assertEquals(-1, record.getAnchorMessageIndex());
        assertEquals("tool_use_result", record.getEventType());
        assertEquals("tool_use", record.getKind());
        assertEquals(3L, record.getFirstSequence());
        assertEquals(5L, record.getSequence());
        assertEquals("success", record.getStatus());
        assertEquals(false, record.getPromptVisible());
        assertEquals(true, record.getPersistable());
        assertTrue(record.getPayloadJson().contains("\"title\":\"读取资源\""));
    }
}
