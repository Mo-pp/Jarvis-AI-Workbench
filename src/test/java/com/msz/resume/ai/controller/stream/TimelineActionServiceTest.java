package com.msz.resume.ai.chat.runtime.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TimelineActionServiceTest {

    private final TimelineActionService service = new TimelineActionService();

    @Test
    @DisplayName("统一 action builder 生成通用 timeline 合同字段")
    void builderAddsCanonicalTimelineFields() {
        ChatRunTraceContext traceContext = new ChatRunTraceContext(
                "run-1",
                "session-1",
                TracePublisher.noop()
        );

        Map<String, Object> payload = service.builder(
                        "action-1",
                        traceContext,
                        new TraceAgentDescriptor("agent-1", "sub", "Explorer", "explorer")
                )
                .toolCallId("call-1")
                .title("读取资源")
                .status("running")
                .summary("正在读取")
                .put("toolName", "openviking_read")
                .build();

        assertEquals("action-1", payload.get("id"));
        assertEquals("call-1", payload.get("toolCallId"));
        assertEquals("run-1", payload.get("runId"));
        assertEquals("sub", payload.get("agentScope"));
        assertEquals("agent-1", payload.get("agentId"));
        assertEquals("Explorer", payload.get("agentLabel"));
        assertEquals("读取资源", payload.get("title"));
        assertEquals("running", payload.get("status"));
        assertEquals("正在读取", payload.get("summary"));
        assertEquals("openviking_read", payload.get("toolName"));
        assertEquals(true, payload.get("persistable"));
        assertEquals(false, payload.get("promptVisible"));
        assertEquals(false, payload.get("sensitive"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    @DisplayName("统一 action builder 支持显式标记持久化和 prompt 可见性")
    void builderCanOverridePersistenceAndPromptVisibility() {
        Map<String, Object> payload = service.builder(
                        "action-2",
                        null,
                        null
                )
                .persistable(false)
                .promptVisible(true)
                .sensitive(true)
                .build();

        assertEquals(false, payload.get("persistable"));
        assertEquals(true, payload.get("promptVisible"));
        assertEquals(true, payload.get("sensitive"));
        assertEquals("", payload.get("runId"));
        assertEquals("main", payload.get("agentScope"));
        assertEquals("main", payload.get("agentId"));
        assertEquals("Main Agent", payload.get("agentLabel"));
    }

}
