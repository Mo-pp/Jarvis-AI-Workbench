package com.msz.resume.ai.chat.runtime.trace;

/**
 * 运行步骤事件发布器。
 */
public interface TracePublisher {

    void publishStep(StepTraceEvent event);

    static TracePublisher noop() {
        return event -> {
        };
    }
}
