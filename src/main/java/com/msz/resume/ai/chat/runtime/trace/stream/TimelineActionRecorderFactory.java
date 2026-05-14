package com.msz.resume.ai.chat.runtime.trace.stream;

import com.msz.resume.ai.chat.runtime.trace.CompositeTimelineActionRecorder;
import com.msz.resume.ai.chat.runtime.trace.TimelineActionRecorder;
import org.springframework.stereotype.Component;

@Component
public class TimelineActionRecorderFactory {

    private final TraceStreamPublisher traceStreamPublisher;
    private final TraceStreamProperties properties;

    public TimelineActionRecorderFactory(TraceStreamPublisher traceStreamPublisher,
                                         TraceStreamProperties properties) {
        this.traceStreamPublisher = traceStreamPublisher;
        this.properties = properties;
    }

    public TimelineActionRecorder withTraceStream(String sessionId, TimelineActionRecorder primary) {
        if (!properties.isEnabled()) {
            return primary != null ? primary : TimelineActionRecorder.noop();
        }
        return CompositeTimelineActionRecorder.of(
                primary,
                new TraceStreamTimelineActionRecorder(sessionId, traceStreamPublisher)
        );
    }
}
