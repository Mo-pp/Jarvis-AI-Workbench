package com.msz.resume.ai.chat.runtime.trace;

import com.msz.resume.ai.chat.api.dto.ChatStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 负责向前端发送结构化 SSE 对话事件。
 *
 * 封装 SseEmitter，提供类型安全的事件发送方法。
 */
@Slf4j
public class ChatStreamEventSink {

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final String sessionId;
    private final TimelineActionRecorder timelineActionRecorder;
    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ChatStreamEventSink(SseEmitter emitter, ObjectMapper objectMapper, String sessionId) {
        this(emitter, objectMapper, sessionId, TimelineActionRecorder.noop());
    }

    public ChatStreamEventSink(SseEmitter emitter,
                               ObjectMapper objectMapper,
                               String sessionId,
                               TimelineActionRecorder timelineActionRecorder) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.sessionId = sessionId;
        this.timelineActionRecorder = timelineActionRecorder != null ? timelineActionRecorder : TimelineActionRecorder.noop();
        this.emitter.onCompletion(() -> closed.set(true));
        this.emitter.onTimeout(() -> closed.set(true));
        this.emitter.onError(error -> closed.set(true));
    }

    /** 发送结构化 SSE 事件 */
    public synchronized void send(String type, Map<String, Object> payload) throws IOException {
        if (closed.get()) {
            return;
        }

        long nextSequence = sequence.incrementAndGet();
        Map<String, Object> eventPayload = payload != null ? payload : Map.of();
        ChatStreamEvent event = ChatStreamEvent.builder()
                .type(type)
                .sessionId(sessionId)
                .sequence(nextSequence)
                .timestamp(Instant.now())
                .payload(eventPayload)
                .build();

        try {
            emitter.send(SseEmitter.event()
                    .name(type)
                    .data(objectMapper.writeValueAsString(event)));
            timelineActionRecorder.record(type, nextSequence, eventPayload);
            if (isHighFrequencyEvent(type)) {
                log.debug("[ChatStreamEventSink] SSE sent: type={}, sessionId={}, sequence={}, runId={}, id={}, parentId={}, kind={}, status={}, title={}",
                        type,
                        sessionId,
                        event.getSequence(),
                        value(payload, "runId"),
                        value(payload, "id"),
                        value(payload, "parentId"),
                        value(payload, "kind"),
                        value(payload, "status"),
                        value(payload, "title"));
            } else {
                log.info("[ChatStreamEventSink] SSE sent: type={}, sessionId={}, sequence={}, runId={}, id={}, parentId={}, kind={}, status={}, title={}",
                        type,
                        sessionId,
                        event.getSequence(),
                        value(payload, "runId"),
                        value(payload, "id"),
                        value(payload, "parentId"),
                        value(payload, "kind"),
                        value(payload, "status"),
                        value(payload, "title"));
            }
        } catch (JsonProcessingException e) {
            throw new IOException("流式事件序列化失败", e);
        } catch (IOException | RuntimeException e) {
            closed.set(true);
            throw e;
        }
    }

    /** 发送错误事件 */
    public void error(String code, String message) {
        try {
            send("error", Map.of(
                    "code", code,
                    "message", message != null ? message : "",
                    "recoverable", false
            ));
        } catch (Exception e) {
            log.warn("[ChatStreamEventSink] error 事件发送失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /** 正常完成 SSE 连接 */
    public void complete() {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    /** 以错误结束 SSE 连接 */
    public void completeWithError(Throwable error) {
        if (closed.compareAndSet(false, true)) {
            emitter.completeWithError(error);
        }
    }

    /** 检查 SSE 连接是否已关闭 */
    public boolean isClosed() {
        return closed.get();
    }

    private static Object value(Map<String, Object> payload, String key) {
        return payload != null ? payload.get(key) : null;
    }

    private static boolean isHighFrequencyEvent(String type) {
        return "message_delta".equals(type);
    }
}
