package com.msz.resume.ai.chat.runtime.trace.stream;

import java.time.Instant;
import java.util.Map;

public record TraceStreamEvent(
        String streamMessageId,
        String eventId,
        String sessionId,
        String runId,
        String eventType,
        String actionId,
        long sequence,
        long firstSequence,
        int anchorMessageIndex,
        Map<String, Object> payload,
        Instant createdAt
) {
}
