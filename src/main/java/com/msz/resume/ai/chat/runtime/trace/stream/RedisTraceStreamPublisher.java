package com.msz.resume.ai.chat.runtime.trace.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.runtime.trace.TimelineActionPayloadProjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class RedisTraceStreamPublisher implements TraceStreamPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TraceStreamProperties properties;
    private final TimelineActionPayloadProjector payloadProjector;

    public RedisTraceStreamPublisher(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     TraceStreamProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.payloadProjector = new TimelineActionPayloadProjector();
    }

    @Override
    public void publishTimelineEvent(String sessionId, String eventType, long sequence, Map<String, Object> payload) {
        if (!properties.isEnabled() || sessionId == null || sessionId.isBlank()) {
            return;
        }

        try {
            Map<String, Object> action = payloadProjector.project(eventType, sequence, payload)
                    .filter(payloadProjector::isPersistable)
                    .orElse(null);
            if (action == null) {
                return;
            }

            String runId = stringValue(action.get("runId"));
            String actionId = stringValue(action.get("id"));
            long firstSequence = longValue(action.get("firstSequence"), sequence);
            String eventId = stableEventId(sessionId, runId, sequence, actionId);
            Instant createdAt = Instant.now();

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("eventId", eventId);
            fields.put("sessionId", sessionId);
            fields.put("runId", runId);
            fields.put("eventType", eventType);
            fields.put("actionId", actionId);
            fields.put("sequence", String.valueOf(sequence));
            fields.put("firstSequence", String.valueOf(firstSequence));
            fields.put("anchorMessageIndex", "-1");
            fields.put("createdAt", createdAt.toString());
            fields.put("payloadJson", objectMapper.writeValueAsString(action));

            StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();
            RedisStreamCommands.XAddOptions options = RedisStreamCommands.XAddOptions.maxlen(Math.max(1L, properties.getMaxLen()))
                    .approximateTrimming(properties.isApproximateTrim());
            RecordId recordId = streamOps.add(properties.getStreamKey(), fields, options);
            log.debug("[TraceStream] published event: streamId={}, eventId={}, sessionId={}, type={}, actionId={}, sequence={}",
                    recordId != null ? recordId.getValue() : null, eventId, sessionId, eventType, actionId, sequence);
        } catch (Exception e) {
            log.warn("[TraceStream] publish failed: sessionId={}, type={}, sequence={}, error={}",
                    sessionId, eventType, sequence, e.getMessage());
        }
    }

    private static String stableEventId(String sessionId, String runId, long sequence, String actionId) {
        String safeRunId = runId != null && !runId.isBlank() ? runId : "no_run";
        String safeActionId = actionId != null && !actionId.isBlank() ? actionId : "no_action";
        return sessionId + ":" + safeRunId + ":" + sequence + ":" + safeActionId;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
