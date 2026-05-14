package com.msz.resume.ai.chat.runtime.trace.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jarvis.trace.stream")
public class TraceStreamProperties {

    private boolean enabled = true;
    private boolean consumerEnabled = true;
    private String streamKey = "jarvis:trace:stream";
    private String group = "trace-db-writer";
    private String consumerName = "jarvis-app";
    private int batchSize = 50;
    private long blockTimeoutMs = 1000;
    private long pollIntervalMs = 1000;
    private long pendingRetryMs = 30000;
    private int pendingRecoveryBatchSize = 20;
    private long claimIdleMs = 30000;
    private long maxDeliveryCount = 5;
    private boolean deadLetterEnabled = true;
    private String deadLetterStreamKey = "jarvis:trace:stream:dead-letter";
    private long maxLen = 10000;
    private boolean approximateTrim = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConsumerEnabled() {
        return consumerEnabled;
    }

    public void setConsumerEnabled(boolean consumerEnabled) {
        this.consumerEnabled = consumerEnabled;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getBlockTimeoutMs() {
        return blockTimeoutMs;
    }

    public void setBlockTimeoutMs(long blockTimeoutMs) {
        this.blockTimeoutMs = blockTimeoutMs;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getPendingRetryMs() {
        return pendingRetryMs;
    }

    public void setPendingRetryMs(long pendingRetryMs) {
        this.pendingRetryMs = pendingRetryMs;
    }

    public int getPendingRecoveryBatchSize() {
        return pendingRecoveryBatchSize;
    }

    public void setPendingRecoveryBatchSize(int pendingRecoveryBatchSize) {
        this.pendingRecoveryBatchSize = pendingRecoveryBatchSize;
    }

    public long getClaimIdleMs() {
        return claimIdleMs;
    }

    public void setClaimIdleMs(long claimIdleMs) {
        this.claimIdleMs = claimIdleMs;
    }

    public long getMaxDeliveryCount() {
        return maxDeliveryCount;
    }

    public void setMaxDeliveryCount(long maxDeliveryCount) {
        this.maxDeliveryCount = maxDeliveryCount;
    }

    public boolean isDeadLetterEnabled() {
        return deadLetterEnabled;
    }

    public void setDeadLetterEnabled(boolean deadLetterEnabled) {
        this.deadLetterEnabled = deadLetterEnabled;
    }

    public String getDeadLetterStreamKey() {
        return deadLetterStreamKey;
    }

    public void setDeadLetterStreamKey(String deadLetterStreamKey) {
        this.deadLetterStreamKey = deadLetterStreamKey;
    }

    public long getMaxLen() {
        return maxLen;
    }

    public void setMaxLen(long maxLen) {
        this.maxLen = maxLen;
    }

    public boolean isApproximateTrim() {
        return approximateTrim;
    }

    public void setApproximateTrim(boolean approximateTrim) {
        this.approximateTrim = approximateTrim;
    }
}
