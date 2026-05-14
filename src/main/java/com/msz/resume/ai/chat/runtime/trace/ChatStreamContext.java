package com.msz.resume.ai.chat.runtime.trace;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前 SSE 流式事件上下文。
 *
 * 用于在状态机执行过程中向活跃的 SSE 连接发送增量消息。
 */
public final class ChatStreamContext {

    private static final Map<String, ChatStreamEventSink> SINKS = new ConcurrentHashMap<>();
    private static final Map<String, ChatRunTraceContext> RUN_CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<String, ChatRunTraceContext> RUN_ID_CONTEXTS = new ConcurrentHashMap<>();

    private ChatStreamContext() {
    }

    /** 绑定会话ID与 SSE Sink */
    public static void bind(String sessionId, ChatStreamEventSink sink) {
        if (sessionId != null && sink != null) {
            SINKS.put(sessionId, sink);
        }
    }

    /** 绑定运行 trace 上下文 */
    public static void bindRun(String sessionId, ChatRunTraceContext traceContext) {
        if (sessionId != null && traceContext != null) {
            RUN_CONTEXTS.put(sessionId, traceContext);
            RUN_ID_CONTEXTS.put(traceContext.runId(), traceContext);
            bind(sessionId, traceContext.sink());
        }
    }

    /** 清除会话的 SSE Sink */
    public static void clear(String sessionId) {
        if (sessionId != null) {
            SINKS.remove(sessionId);
            ChatRunTraceContext removed = RUN_CONTEXTS.remove(sessionId);
            if (removed != null) {
                RUN_ID_CONTEXTS.remove(removed.runId());
            }
        }
    }

    /** 检查会话的 SSE 连接是否活跃 */
    public static boolean isActive(String sessionId) {
        ChatStreamEventSink sink = SINKS.get(sessionId);
        return sink != null && !sink.isClosed();
    }

    /** 获取会话当前运行的 trace 上下文 */
    public static ChatRunTraceContext getTraceContext(String sessionId) {
        return RUN_CONTEXTS.get(sessionId);
    }

    /** 先按 sessionId 查，再按 runId 兜底查，支持子 Agent 透传到父 SSE。 */
    public static ChatRunTraceContext getTraceContext(String sessionId, String runId) {
        ChatRunTraceContext context = sessionId != null ? RUN_CONTEXTS.get(sessionId) : null;
        if (context != null) {
            return context;
        }
        return runId != null ? RUN_ID_CONTEXTS.get(runId) : null;
    }

    /** 向会话发送增量文本消息 */
    public static void sendDelta(String sessionId, String delta) throws IOException {
        ChatStreamEventSink sink = SINKS.get(sessionId);
        if (sink == null || sink.isClosed() || delta == null || delta.isEmpty()) {
            return;
        }
        sink.send("message_delta", Map.of(
                "role", "assistant",
                "delta", delta
        ));
    }
}
