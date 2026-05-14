package com.msz.resume.ai.chat.runtime.trace;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the latest payload for each visible action id during one SSE run.
 */
public class InMemoryTimelineActionRecorder implements TimelineActionRecorder {

    private final Map<String, Map<String, Object>> actionsById = new ConcurrentHashMap<>();
    private final TimelineActionPayloadProjector payloadProjector = new TimelineActionPayloadProjector();

    @Override
    public void record(String eventType, long sequence, Map<String, Object> payload) {
        Map<String, Object> action = payloadProjector.project(eventType, sequence, payload).orElse(null);
        if (action == null) {
            return;
        }
        String id = String.valueOf(action.getOrDefault("id", ""));

        actionsById.merge(id, action, (previous, next) -> {
            Map<String, Object> merged = new LinkedHashMap<>(previous);
            merged.putAll(next);
            Object previousFirstSequence = previous.get("firstSequence");
            merged.put("firstSequence", previousFirstSequence != null ? previousFirstSequence : previous.get("sequence"));
            merged.put("sequence", next.get("sequence"));
            merged.put("eventType", eventType);
            merged.put("kind", TimelineActionPayloadProjector.kindFor(eventType));
            return merged;
        });
    }

    @Override
    public List<Map<String, Object>> snapshot() {
        return actionsById.values().stream()
                .map(action -> (Map<String, Object>) new LinkedHashMap<>(action))
                .sorted(Comparator
                        .comparingLong(InMemoryTimelineActionRecorder::firstSequence)
                        .thenComparing(action -> String.valueOf(action.getOrDefault("id", ""))))
                .toList();
    }

    private static long firstSequence(Map<String, Object> action) {
        Object value = action.get("firstSequence");
        if (value == null) {
            value = action.get("sequence");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return Long.MAX_VALUE;
        }
    }
}
