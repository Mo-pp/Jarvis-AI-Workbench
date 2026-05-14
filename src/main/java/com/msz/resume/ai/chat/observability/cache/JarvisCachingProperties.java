package com.msz.resume.ai.chat.observability.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存追踪配置类
 *
 * <p>控制LLM API前缀缓存的追踪行为，包括热度判定阈值和告警设置。
 *
 * <p>配置示例（application-dev.yml）：
 * <pre>
 * jarvis:
 *   caching:
 *     enabled: true
 *     warm-threshold: 0.3
 *     hot-threshold: 0.7
 *     max-consecutive-misses: 5
 *     log-details: true
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "jarvis.caching")
public class JarvisCachingProperties {

    /** 是否启用缓存追踪 */
    private boolean enabled = true;

    /** WARM热度阈值（命中率 > 此值为WARM） */
    private double warmThreshold = 0.3;

    /** HOT热度阈值（命中率 > 此值为HOT） */
    private double hotThreshold = 0.7;

    /** 连续未命中告警阈值 */
    private int maxConsecutiveMisses = 5;

    /** 是否记录详细日志 */
    private boolean logDetails = true;

    // ==================== Getters & Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getWarmThreshold() {
        return warmThreshold;
    }

    public void setWarmThreshold(double warmThreshold) {
        this.warmThreshold = warmThreshold;
    }

    public double getHotThreshold() {
        return hotThreshold;
    }

    public void setHotThreshold(double hotThreshold) {
        this.hotThreshold = hotThreshold;
    }

    public int getMaxConsecutiveMisses() {
        return maxConsecutiveMisses;
    }

    public void setMaxConsecutiveMisses(int maxConsecutiveMisses) {
        this.maxConsecutiveMisses = maxConsecutiveMisses;
    }

    public boolean isLogDetails() {
        return logDetails;
    }

    public void setLogDetails(boolean logDetails) {
        this.logDetails = logDetails;
    }
}
