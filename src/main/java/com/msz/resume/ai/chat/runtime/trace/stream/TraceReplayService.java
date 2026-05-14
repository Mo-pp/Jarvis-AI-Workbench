package com.msz.resume.ai.chat.runtime.trace.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.api.dto.ChatStreamEvent;
import com.msz.resume.ai.chat.session.entity.TimelineActionRecord;
import com.msz.resume.ai.chat.session.mapper.TimelineActionRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TraceReplayService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TraceStreamProperties properties;
    private final TimelineActionRecordMapper timelineActionRecordMapper;

    public TraceReplayService(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              TraceStreamProperties properties,
                              TimelineActionRecordMapper timelineActionRecordMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.timelineActionRecordMapper = timelineActionRecordMapper;
    }

    public List<ChatStreamEvent> replaySince(String sessionId, long lastSequence, int count) {
        return replaySince(sessionId, null, lastSequence, count);
    }

    public List<ChatStreamEvent> replaySince(String sessionId, String runId, long lastSequence, int count) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }

        int requested = Math.max(1, count);
        List<ChatStreamEvent> merged = new ArrayList<>();
        merged.addAll(replayFromDatabase(sessionId, runId, lastSequence, requested));
        merged.addAll(replayFromRedis(sessionId, runId, lastSequence, requested));
        return merged.stream()
                .collect(LinkedHashMap<String, ChatStreamEvent>::new,
                        (events, event) -> events.merge(dedupKey(event), event, TraceReplayService::preferReplayEvent),
                        Map::putAll)
                .values()
                .stream()
                .sorted(Comparator.comparingLong(ChatStreamEvent::getSequence))
                .limit(requested)
                .toList();
    }

    private List<ChatStreamEvent> replayFromDatabase(String sessionId, String runId, long lastSequence, int count) {
        try {
            List<TimelineActionRecord> records = timelineActionRecordMapper.selectReplayBySessionId(
                    sessionId,
                    runId,
                    lastSequence,
                    count
            );
            return records.stream()
                    .map(record -> toReplayEvent(sessionId, record))
                    .filter(event -> event.getSequence() > lastSequence)
                    .filter(event -> runId == null || runId.isBlank() || runId.equals(stringValue(event.getPayload().get("runId"))))
                    .toList();
        } catch (Exception e) {
            log.warn("[TraceReplay] database replay failed: sessionId={}, runId={}, lastSequence={}, error={}",
                    sessionId, runId, lastSequence, e.getMessage());
            return List.of();
        }
    }

    private List<ChatStreamEvent> replayFromRedis(String sessionId, String runId, long lastSequence, int count) {
        try {
            int scanLimit = Math.min(Math.max(count * 8, 200), 2000);
            var records = redisTemplate.opsForStream().reverseRange(
                    properties.getStreamKey(),
                    Range.unbounded(),
                    Limit.limit().count(scanLimit)
            );

            List<ChatStreamEvent> replay = new ArrayList<>();
            for (Object rawRecord : records) {
                @SuppressWarnings("unchecked")
                MapRecord<String, Object, Object> record = (MapRecord<String, Object, Object>) rawRecord;
                Map<Object, Object> fields = record.getValue();
                if (!sessionId.equals(stringValue(fields.get("sessionId")))) {
                    continue;
                }
                if (runId != null && !runId.isBlank() && !runId.equals(stringValue(fields.get("runId")))) {
                    continue;
                }

                long sequence = longValue(fields.get("sequence"), 0L);
                if (sequence <= lastSequence) {
                    continue;
                }

                Map<String, Object> payload = parsePayload(fields.get("payloadJson"));
                String eventType = stringValue(fields.get("eventType"));
                replay.add(ChatStreamEvent.builder()
                        .type(eventType)
                        .sessionId(sessionId)
                        .sequence(sequence)
                        .timestamp(parseInstant(fields.get("createdAt")))
                        .payload(payload)
                        .build());
            }
            return replay;
        } catch (Exception e) {
            log.warn("[TraceReplay] redis replay failed: sessionId={}, runId={}, lastSequence={}, error={}",
                    sessionId, runId, lastSequence, e.getMessage());
            return List.of();
        }
    }

    private ChatStreamEvent toReplayEvent(String sessionId, TimelineActionRecord record) {
        Map<String, Object> payload = parsePayload(record.getPayloadJson());
        return ChatStreamEvent.builder()
                .type(firstNonBlank(record.getEventType(), stringValue(payload.get("eventType"))))
                .sessionId(sessionId)
                .sequence(record.getSequence() != null ? record.getSequence() : 0L)
                .timestamp(toInstant(record.getUpdatedAt(), record.getCreatedAt()))
                .payload(payload)
                .build();
    }

    private Map<String, Object> parsePayload(Object payloadJson) {
        try {
            if (payloadJson == null) {
                return Map.of();
            }
            return objectMapper.readValue(String.valueOf(payloadJson), MAP_TYPE);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("rawPayloadJson", String.valueOf(payloadJson));
            return fallback;
        }
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static long longValue(Object value, long fallback) {
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Instant parseInstant(Object value) {
        try {
            return value != null ? Instant.parse(String.valueOf(value)) : Instant.now();
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private static Instant toInstant(LocalDateTime primary, LocalDateTime fallback) {
        LocalDateTime value = primary != null ? primary : fallback;
        return value != null ? value.atZone(ZoneId.systemDefault()).toInstant() : Instant.now();
    }

    private static String dedupKey(ChatStreamEvent event) {
        Map<String, Object> payload = event.getPayload();
        String runId = payload != null ? stringValue(payload.get("runId")) : "";
        String actionId = payload != null ? stringValue(payload.get("id")) : "";
        if (!actionId.isBlank()) {
            return event.getSessionId() + ":" + runId + ":" + actionId + ":" + event.getType() + ":" + event.getSequence();
        }
        return event.getSessionId() + ":" + runId + ":" + event.getType() + ":" + event.getSequence();
    }

    private static ChatStreamEvent preferReplayEvent(ChatStreamEvent left, ChatStreamEvent right) {
        if (right.getSequence() > left.getSequence()) {
            return right;
        }
        if (right.getSequence() < left.getSequence()) {
            return left;
        }
        return right;
    }
}
