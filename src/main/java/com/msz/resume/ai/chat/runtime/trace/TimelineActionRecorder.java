package com.msz.resume.ai.chat.runtime.trace;

import java.util.List;
import java.util.Map;

/**
 * Collects user-visible action events for persisted chat history replay.
 */
public interface TimelineActionRecorder {

    void record(String eventType, long sequence, Map<String, Object> payload);

    List<Map<String, Object>> snapshot();

    static TimelineActionRecorder noop() {
        return new TimelineActionRecorder() {
            @Override
            public void record(String eventType, long sequence, Map<String, Object> payload) {
                // no-op
            }

            @Override
            public List<Map<String, Object>> snapshot() {
                return List.of();
            }
        };
    }
}
