package com.msz.resume.ai.chat.runtime.trace.stream;

import com.msz.resume.ai.chat.runtime.trace.TimelineActionRecorder;

import java.util.List;
import java.util.Map;

public class TraceStreamTimelineActionRecorder implements TimelineActionRecorder {

    private final String sessionId;
    private final TraceStreamPublisher publisher;

    public TraceStreamTimelineActionRecorder(String sessionId, TraceStreamPublisher publisher) {
        this.sessionId = sessionId;
        this.publisher = publisher;
    }

    @Override
    public void record(String eventType, long sequence, Map<String, Object> payload) {
        publisher.publishTimelineEvent(sessionId, eventType, sequence, payload);
    }

    @Override
    public List<Map<String, Object>> snapshot() {
        return List.of();
    }
}
