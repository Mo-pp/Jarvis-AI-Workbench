package com.msz.resume.ai.chat.runtime.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatStreamContextTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final String sessionId = "thinking-session";

    @AfterEach
    void tearDown() {
        ChatStreamContext.clear(sessionId);
    }

    @Test
    @DisplayName("Thinking 事件应按约定 payload 发送给当前 SSE")
    void shouldSendThinkingEvents() throws Exception {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        ChatStreamEventSink sink = new ChatStreamEventSink(emitter, OBJECT_MAPPER, sessionId);
        ChatStreamContext.bind(sessionId, sink);

        ChatStreamContext.sendThinkingStarted(sessionId);
        ChatStreamContext.sendThinkingDone(sessionId, "success");

        JsonNode started = emitter.eventJsonAt(0);
        JsonNode done = emitter.eventJsonAt(1);

        assertEquals("thinking_started", started.path("type").asText());
        assertEquals("hidden", started.path("payload").path("mode").asText());
        assertEquals("gpt", started.path("payload").path("provider").asText());
        assertFalse(started.path("payload").path("summaryAvailable").asBoolean());

        assertEquals("thinking_done", done.path("type").asText());
        assertEquals("success", done.path("payload").path("status").asText());
        assertEquals("hidden", done.path("payload").path("mode").asText());
        assertEquals("gpt", done.path("payload").path("provider").asText());
        assertFalse(done.path("payload").path("summaryAvailable").asBoolean());
    }

    private static class RecordingSseEmitter extends SseEmitter {
        private final List<String> eventBodies = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                Object data = item.getData();
                if (data instanceof String value && value.startsWith("{")) {
                    eventBodies.add(value);
                }
            }
        }

        JsonNode eventJsonAt(int index) throws Exception {
            return OBJECT_MAPPER.readTree(eventBodies.get(index));
        }
    }
}
