package com.msz.resume.ai.chat.runtime.trace.stream;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TraceStreamDebugService {

    private final StringRedisTemplate redisTemplate;
    private final TraceStreamProperties properties;
    private final TraceStreamMetrics metrics;

    public TraceStreamDebugService(StringRedisTemplate redisTemplate,
                                   TraceStreamProperties properties,
                                   TraceStreamMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("consumerEnabled", properties.isConsumerEnabled());
        status.put("streamKey", properties.getStreamKey());
        status.put("group", properties.getGroup());
        status.put("consumerName", properties.getConsumerName());
        status.put("batchSize", properties.getBatchSize());
        status.put("blockTimeoutMs", properties.getBlockTimeoutMs());
        status.put("pollIntervalMs", properties.getPollIntervalMs());
        status.put("pendingRetryMs", properties.getPendingRetryMs());
        status.put("pendingRecoveryBatchSize", properties.getPendingRecoveryBatchSize());
        status.put("claimIdleMs", properties.getClaimIdleMs());
        status.put("maxDeliveryCount", properties.getMaxDeliveryCount());
        status.put("deadLetterEnabled", properties.isDeadLetterEnabled());
        status.put("deadLetterStreamKey", properties.getDeadLetterStreamKey());
        status.put("maxLen", properties.getMaxLen());
        status.put("approximateTrim", properties.isApproximateTrim());

        status.put("streamInfo", streamInfo(properties.getStreamKey()));
        status.put("groupInfo", groupInfo(properties.getStreamKey(), properties.getGroup()));
        status.put("consumerInfo", consumerInfo(properties.getStreamKey(), properties.getGroup()));
        status.put("pendingSummary", pendingSummary(properties.getStreamKey(), properties.getGroup()));
        status.put("backlogEstimate", backlogEstimate(properties.getStreamKey(), properties.getGroup()));
        status.put("metrics", metrics.snapshot());
        status.put("deadLetterStreamInfo", streamInfo(properties.getDeadLetterStreamKey()));
        return status;
    }

    public List<Map<String, Object>> recentEvents(int count) {
        return recentRecords(properties.getStreamKey(), count);
    }

    public List<Map<String, Object>> recentDeadLetters(int count) {
        return recentRecords(properties.getDeadLetterStreamKey(), count);
    }

    private Map<String, Object> streamInfo(String streamKey) {
        try {
            StreamInfo.XInfoStream info = redisTemplate.opsForStream().info(streamKey);
            if (info == null) {
                return Map.of("exists", false);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", true);
            result.put("length", info.streamLength());
            result.put("groupCount", info.groupCount());
            result.put("lastGeneratedId", info.lastGeneratedId());
            result.put("firstEntryId", info.firstEntryId());
            result.put("lastEntryId", info.lastEntryId());
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    private List<Map<String, Object>> groupInfo(String streamKey, String group) {
        try {
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
            return groups.stream()
                    .filter(item -> group.equals(item.groupName()))
                    .map(item -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("groupName", item.groupName());
                        result.put("consumerCount", item.consumerCount());
                        result.put("pendingCount", item.pendingCount());
                        result.put("lastDeliveredId", item.lastDeliveredId());
                        return result;
                    })
                    .toList();
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            return List.of(result);
        }
    }

    private List<Map<String, Object>> consumerInfo(String streamKey, String group) {
        try {
            StreamInfo.XInfoConsumers consumers = redisTemplate.opsForStream().consumers(streamKey, group);
            return consumers.stream()
                    .map(item -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("groupName", item.groupName());
                        result.put("consumerName", item.consumerName());
                        result.put("idleTimeMs", item.idleTimeMs());
                        result.put("pendingCount", item.pendingCount());
                        return result;
                    })
                    .toList();
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            return List.of(result);
        }
    }

    private Map<String, Object> pendingSummary(String streamKey, String group) {
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream().pending(streamKey, group);
            if (summary == null) {
                return Map.of("exists", false);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", true);
            result.put("groupName", summary.getGroupName());
            result.put("totalPendingMessages", summary.getTotalPendingMessages());
            result.put("minMessageId", summary.minMessageId());
            result.put("maxMessageId", summary.maxMessageId());
            result.put("pendingMessagesPerConsumer", summary.getPendingMessagesPerConsumer());
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    private Map<String, Object> backlogEstimate(String streamKey, String group) {
        try {
            StreamInfo.XInfoStream stream = redisTemplate.opsForStream().info(streamKey);
            StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(streamKey);
            if (stream == null || groups == null) {
                return Map.of("exists", false);
            }
            return groups.stream()
                    .filter(item -> group.equals(item.groupName()))
                    .findFirst()
                    .map(item -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("exists", true);
                        result.put("streamLength", stream.streamLength());
                        result.put("lastGeneratedId", stream.lastGeneratedId());
                        result.put("lastDeliveredId", item.lastDeliveredId());
                        result.put("pendingCount", item.pendingCount());
                        result.put("lagAvailable", false);
                        return result;
                    })
                    .orElseGet(() -> Map.of("exists", false));
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    private List<Map<String, Object>> recentRecords(String streamKey, int count) {
        try {
            var records = redisTemplate.opsForStream().reverseRange(
                    streamKey,
                    Range.unbounded(),
                    Limit.limit().count(Math.max(1, count))
            );
            return records.stream()
                    .map(record -> {
                        @SuppressWarnings("unchecked")
                        MapRecord<String, Object, Object> typedRecord = (MapRecord<String, Object, Object>) record;
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", typedRecord.getId().getValue());
                        item.put("stream", streamKey);
                        item.put("fields", new LinkedHashMap<>(typedRecord.getValue()));
                        return item;
                    })
                    .toList();
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            return List.of(result);
        }
    }
}
