package com.msz.resume.ai.chat.runtime.trace.stream;

import java.util.Map;

public interface TraceStreamPublisher {

    void publishTimelineEvent(String sessionId, String eventType, long sequence, Map<String, Object> payload);
}
