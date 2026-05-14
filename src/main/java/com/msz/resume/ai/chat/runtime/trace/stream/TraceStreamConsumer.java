package com.msz.resume.ai.chat.runtime.trace.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.entity.TimelineActionRecord;
import com.msz.resume.ai.chat.session.mapper.TimelineActionRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TraceStreamConsumer {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TraceStreamProperties properties;
    private final TimelineActionRecordProjector recordProjector;
    private final TimelineActionRecordMapper timelineActionMapper;
    private final TraceStreamMetrics metrics;
    private volatile boolean groupReady = false;
    private volatile long lastPendingRecoveryAt = 0L;

    public TraceStreamConsumer(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               TraceStreamProperties properties,
                               TimelineActionRecordProjector recordProjector,
                               TimelineActionRecordMapper timelineActionMapper,
                               TraceStreamMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.recordProjector = recordProjector;
        this.timelineActionMapper = timelineActionMapper;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${jarvis.trace.stream.poll-interval-ms:1000}")
    public void poll() {
        if (!properties.isEnabled() || !properties.isConsumerEnabled()) {
            return;
        }

        try {
            if (!ensureGroup()) {
                return;
            }

            recoverPendingIfDue();

            StreamOperations<String, String, String> streamOps = redisTemplate.opsForStream();
            List<MapRecord<String, String, String>> records = streamOps.read(
                    Consumer.from(properties.getGroup(), properties.getConsumerName()),
                    StreamReadOptions.empty()
                            .count(Math.max(1, properties.getBatchSize()))
                            .block(Duration.ofMillis(Math.max(1, properties.getBlockTimeoutMs()))),
                    StreamOffset.create(properties.getStreamKey(), ReadOffset.lastConsumed())
            );

            if (records == null || records.isEmpty()) {
                return;
            }

            consume(records);
        } catch (Exception e) {
            metrics.recordPollFailure();
            log.warn("[TraceStream] consumer poll failed: stream={}, group={}, error={}",
                    properties.getStreamKey(), properties.getGroup(), e.getMessage());
        }
    }

    private void consume(List<MapRecord<String, String, String>> records) {
        long startedAt = System.nanoTime();
        List<TimelineActionRecord> timelineRecords = new ArrayList<>();
        List<RecordId> ackIds = new ArrayList<>();
        long maxEventLagMs = 0L;

        for (MapRecord<String, String, String> record : records) {
            try {
                TraceStreamEvent event = toEvent(record);
                maxEventLagMs = Math.max(maxEventLagMs, eventLagMs(event));
                TimelineActionRecord timelineRecord = recordProjector.project(event)
                        .orElseThrow(() -> new IllegalArgumentException("event cannot be projected to timeline action"));
                timelineRecords.add(timelineRecord);
                ackIds.add(record.getId());
            } catch (Exception e) {
                metrics.recordInvalidEvent();
                log.warn("[TraceStream] invalid event left pending: streamId={}, error={}",
                        record.getId().getValue(), e.getMessage());
            }
        }

        try {
            if (!timelineRecords.isEmpty()) {
                timelineActionMapper.upsertBatch(timelineRecords);
            }
            acknowledge(ackIds);
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            metrics.recordBatch(records.size(), timelineRecords.size(), ackIds.size(), durationMs, maxEventLagMs);
            log.debug("[TraceStream] consumed events: read={}, persisted={}, acked={}, durationMs={}, maxEventLagMs={}",
                    records.size(), timelineRecords.size(), ackIds.size(), durationMs, maxEventLagMs);
        } catch (Exception e) {
            metrics.recordPersistenceFailure();
            log.warn("[TraceStream] persistence failed, records left pending: read={}, persistable={}, error={}",
                    records.size(), timelineRecords.size(), e.getMessage());
        }
    }

    private void recoverPendingIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastPendingRecoveryAt < Math.max(1L, properties.getPendingRetryMs())) {
            return;
        }
        lastPendingRecoveryAt = now;

        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream().pending(
                    properties.getStreamKey(),
                    properties.getGroup(),
                    Range.unbounded(),
                    Math.max(1, properties.getPendingRecoveryBatchSize())
            );
            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            List<RecordId> claimIds = new ArrayList<>();
            List<RecordId> deadLetterAckIds = new ArrayList<>();
            for (PendingMessage pendingMessage : pendingMessages) {
                if (!isEligibleForClaim(pendingMessage)) {
                    continue;
                }
                if (isExceededRetryLimit(pendingMessage)) {
                    if (deadLetter(pendingMessage)) {
                        deadLetterAckIds.add(pendingMessage.getId());
                    }
                    continue;
                }
                claimIds.add(pendingMessage.getId());
            }
            acknowledge(deadLetterAckIds);
            if (claimIds.isEmpty()) {
                return;
            }

            var claimed = redisTemplate.opsForStream().claim(
                    properties.getStreamKey(),
                    properties.getGroup(),
                    properties.getConsumerName(),
                    Duration.ofMillis(Math.max(1L, properties.getClaimIdleMs())),
                    claimIds.toArray(RecordId[]::new)
            );
            if (claimed != null && !claimed.isEmpty()) {
                log.debug("[TraceStream] recovered pending events: count={}", claimed.size());
                consume(castRecords(claimed));
            }
        } catch (Exception e) {
            metrics.recordPendingRecoveryFailure();
            log.warn("[TraceStream] pending recovery failed: stream={}, group={}, error={}",
                    properties.getStreamKey(), properties.getGroup(), e.getMessage());
        }
    }

    private boolean ensureGroup() {
        if (groupReady) {
            return true;
        }
        try {
            redisTemplate.opsForStream().createGroup(
                    properties.getStreamKey(),
                    ReadOffset.from("0-0"),
                    properties.getGroup()
            );
            groupReady = true;
            log.info("[TraceStream] consumer group created: stream={}, group={}",
                    properties.getStreamKey(), properties.getGroup());
            return true;
        } catch (DataAccessException e) {
            String message = e.getMessage() != null ? e.getMessage() : "";
            if (message.contains("BUSYGROUP")) {
                groupReady = true;
                return true;
            }
            if (message.contains("no such key") || message.contains("NOGROUP")) {
                return false;
            }
            log.warn("[TraceStream] create group failed: stream={}, group={}, error={}",
                    properties.getStreamKey(), properties.getGroup(), e.getMessage());
            return false;
        }
    }

    private TraceStreamEvent toEvent(MapRecord<String, String, String> record) throws Exception {
        Map<String, String> fields = record.getValue();
        Map<String, Object> payload = objectMapper.readValue(fields.getOrDefault("payloadJson", "{}"), PAYLOAD_TYPE);
        Instant createdAt = parseInstant(fields.get("createdAt"));
        return new TraceStreamEvent(
                record.getId().getValue(),
                fields.getOrDefault("eventId", ""),
                fields.getOrDefault("sessionId", ""),
                fields.getOrDefault("runId", ""),
                fields.getOrDefault("eventType", ""),
                fields.getOrDefault("actionId", ""),
                longValue(fields.get("sequence"), 0L),
                longValue(fields.get("firstSequence"), longValue(fields.get("sequence"), 0L)),
                (int) longValue(fields.get("anchorMessageIndex"), -1L),
                payload,
                createdAt
        );
    }

    private void acknowledge(List<RecordId> ackIds) {
        if (ackIds == null || ackIds.isEmpty()) {
            return;
        }
        redisTemplate.opsForStream().acknowledge(
                properties.getStreamKey(),
                properties.getGroup(),
                ackIds.toArray(RecordId[]::new)
        );
    }

    private boolean isEligibleForClaim(PendingMessage pendingMessage) {
        return pendingMessage != null
                && pendingMessage.getId() != null
                && pendingMessage.getElapsedTimeSinceLastDelivery() != null
                && pendingMessage.getElapsedTimeSinceLastDelivery().toMillis() >= Math.max(1L, properties.getClaimIdleMs());
    }

    private boolean isExceededRetryLimit(PendingMessage pendingMessage) {
        return pendingMessage != null
                && pendingMessage.getTotalDeliveryCount() >= Math.max(1L, properties.getMaxDeliveryCount());
    }

    private boolean deadLetter(PendingMessage pendingMessage) {
        if (pendingMessage == null || pendingMessage.getId() == null) {
            return false;
        }

        if (!properties.isDeadLetterEnabled()) {
            log.error("[TraceStream] poisoned pending message reached retry limit but dead-letter is disabled: streamId={}, deliveryCount={}",
                    pendingMessage.getId().getValue(), pendingMessage.getTotalDeliveryCount());
            return false;
        }

        try {
            Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("sourceStreamKey", properties.getStreamKey());
            fields.put("sourceGroup", properties.getGroup());
            fields.put("streamMessageId", pendingMessage.getId().getValue());
            fields.put("consumerName", pendingMessage.getConsumerName());
            fields.put("deliveryCount", String.valueOf(pendingMessage.getTotalDeliveryCount()));
            fields.put("elapsedMs", String.valueOf(pendingMessage.getElapsedTimeSinceLastDelivery() != null
                    ? pendingMessage.getElapsedTimeSinceLastDelivery().toMillis() : 0L));
            fields.put("deadLetteredAt", Instant.now().toString());
            readRecordById(pendingMessage.getId()).ifPresent(record -> {
                for (Map.Entry<String, String> entry : record.getValue().entrySet()) {
                    fields.put("original." + entry.getKey(), entry.getValue());
                }
            });
            redisTemplate.opsForStream().add(
                    properties.getDeadLetterStreamKey(),
                    fields,
                    RedisStreamCommands.XAddOptions.maxlen(Math.max(1L, properties.getMaxLen()))
                            .approximateTrimming(properties.isApproximateTrim())
            );
            log.error("[TraceStream] moved pending message to dead-letter stream: streamId={}, deliveryCount={}, deadLetterStream={}",
                    pendingMessage.getId().getValue(), pendingMessage.getTotalDeliveryCount(), properties.getDeadLetterStreamKey());
            metrics.recordDeadLetter();
            return true;
        } catch (Exception e) {
            log.error("[TraceStream] dead-letter publish failed: streamId={}, deliveryCount={}, error={}",
                    pendingMessage.getId().getValue(), pendingMessage.getTotalDeliveryCount(), e.getMessage());
            return false;
        }
    }

    private java.util.Optional<MapRecord<String, String, String>> readRecordById(RecordId recordId) {
        try {
            var records = redisTemplate.opsForStream().range(
                    properties.getStreamKey(),
                    Range.just(recordId.getValue())
            );
            if (records == null || records.isEmpty()) {
                return java.util.Optional.empty();
            }
            @SuppressWarnings("unchecked")
            MapRecord<String, String, String> record = (MapRecord<String, String, String>) (MapRecord<?, ?, ?>) records.getFirst();
            return java.util.Optional.of(record);
        } catch (Exception e) {
            log.warn("[TraceStream] failed to read pending source record for dead-letter: streamId={}, error={}",
                    recordId.getValue(), e.getMessage());
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<MapRecord<String, String, String>> castRecords(List<?> records) {
        return records.stream()
                .map(record -> (MapRecord<String, String, String>) record)
                .toList();
    }

    private static long eventLagMs(TraceStreamEvent event) {
        if (event == null || event.createdAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(event.createdAt(), Instant.now()).toMillis());
    }

    private static Instant parseInstant(String value) {
        try {
            return value != null && !value.isBlank() ? Instant.parse(value) : Instant.now();
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private static long longValue(String value, long fallback) {
        try {
            return value != null && !value.isBlank() ? Long.parseLong(value) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
