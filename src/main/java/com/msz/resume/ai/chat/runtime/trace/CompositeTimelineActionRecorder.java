package com.msz.resume.ai.chat.runtime.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fan-out recorder used when one SSE event should feed multiple timeline sinks.
 */
public class CompositeTimelineActionRecorder implements TimelineActionRecorder {

    private final List<TimelineActionRecorder> delegates;

    public CompositeTimelineActionRecorder(List<TimelineActionRecorder> delegates) {
        this.delegates = delegates != null
                ? delegates.stream().filter(java.util.Objects::nonNull).toList()
                : List.of();
    }

    public static TimelineActionRecorder of(TimelineActionRecorder... delegates) {
        if (delegates == null || delegates.length == 0) {
            return TimelineActionRecorder.noop();
        }
        return new CompositeTimelineActionRecorder(List.of(delegates));
    }

    @Override
    public void record(String eventType, long sequence, Map<String, Object> payload) {
        for (TimelineActionRecorder delegate : delegates) {
            delegate.record(eventType, sequence, payload);
        }
    }

    @Override
    public List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (TimelineActionRecorder delegate : delegates) {
            snapshots.addAll(delegate.snapshot());
        }
        return snapshots;
    }
}
