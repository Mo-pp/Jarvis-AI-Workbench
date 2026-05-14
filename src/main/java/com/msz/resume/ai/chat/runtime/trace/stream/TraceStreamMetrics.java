package com.msz.resume.ai.chat.runtime.trace.stream;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TraceStreamMetrics {

    private final AtomicLong pollFailureCount = new AtomicLong();
    private final AtomicLong persistenceFailureCount = new AtomicLong();
    private final AtomicLong invalidEventCount = new AtomicLong();
    private final AtomicLong pendingRecoveryFailureCount = new AtomicLong();
    private final AtomicLong deadLetterCount = new AtomicLong();
    private final AtomicLong totalRead = new AtomicLong();
    private final AtomicLong totalPersisted = new AtomicLong();
    private final AtomicLong totalAcked = new AtomicLong();
    private final AtomicLong lastBatchRead = new AtomicLong();
    private final AtomicLong lastBatchPersisted = new AtomicLong();
    private final AtomicLong lastBatchAcked = new AtomicLong();
    private final AtomicLong lastBatchDurationMs = new AtomicLong();
    private final AtomicLong lastBatchMaxEventLagMs = new AtomicLong();
    private final AtomicLong lastConsumedAtEpochMs = new AtomicLong();

    public void recordPollFailure() {
        pollFailureCount.incrementAndGet();
    }

    public void recordPersistenceFailure() {
        persistenceFailureCount.incrementAndGet();
    }

    public void recordInvalidEvent() {
        invalidEventCount.incrementAndGet();
    }

    public void recordPendingRecoveryFailure() {
        pendingRecoveryFailureCount.incrementAndGet();
    }

    public void recordDeadLetter() {
        deadLetterCount.incrementAndGet();
    }

    public void recordBatch(int read, int persisted, int acked, long durationMs, long maxEventLagMs) {
        totalRead.addAndGet(Math.max(0, read));
        totalPersisted.addAndGet(Math.max(0, persisted));
        totalAcked.addAndGet(Math.max(0, acked));
        lastBatchRead.set(Math.max(0, read));
        lastBatchPersisted.set(Math.max(0, persisted));
        lastBatchAcked.set(Math.max(0, acked));
        lastBatchDurationMs.set(Math.max(0L, durationMs));
        lastBatchMaxEventLagMs.set(Math.max(0L, maxEventLagMs));
        lastConsumedAtEpochMs.set(System.currentTimeMillis());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalRead", totalRead.get());
        metrics.put("totalPersisted", totalPersisted.get());
        metrics.put("totalAcked", totalAcked.get());
        metrics.put("lastBatchRead", lastBatchRead.get());
        metrics.put("lastBatchPersisted", lastBatchPersisted.get());
        metrics.put("lastBatchAcked", lastBatchAcked.get());
        metrics.put("lastBatchDurationMs", lastBatchDurationMs.get());
        metrics.put("lastBatchMaxEventLagMs", lastBatchMaxEventLagMs.get());
        metrics.put("lastConsumedAt", lastConsumedAt());
        metrics.put("pollFailureCount", pollFailureCount.get());
        metrics.put("persistenceFailureCount", persistenceFailureCount.get());
        metrics.put("invalidEventCount", invalidEventCount.get());
        metrics.put("pendingRecoveryFailureCount", pendingRecoveryFailureCount.get());
        metrics.put("deadLetterCount", deadLetterCount.get());
        return metrics;
    }

    private String lastConsumedAt() {
        long epochMs = lastConsumedAtEpochMs.get();
        return epochMs > 0 ? Instant.ofEpochMilli(epochMs).toString() : "";
    }
}
