package com.msz.resume.ai.chat.compression.model;

import java.io.Serializable;

/**
 * 缓存使用情况记录
 *
 * <p>追踪LLM API的前缀缓存命中情况，包含Token使用量、命中率、热度状态等信息。
 *
 * <p>使用示例：
 * <pre>{@code
 * CacheUsage usage = cacheTracker.track(response);
 * log.info("缓存命中率: {}%, 热度: {}", usage.hitRate() * 100, usage.warmth());
 * if (usage.shouldAlert()) {
 *     log.warn("缓存连续未命中: {}次", usage.consecutiveMisses());
 * }
 * }</pre>
 *
 * @param promptTokens      输入Token数
 * @param completionTokens  输出Token数
 * @param cachedTokens      缓存命中的Token数
 * @param hitRate           缓存命中率（0.0 ~ 1.0）
 * @param warmth            缓存热度状态
 * @param consecutiveMisses 连续未命中次数
 * @param shouldAlert       是否需要告警（连续未命中达到阈值）
 */
public record CacheUsage(
    int promptTokens,
    int completionTokens,
    int cachedTokens,
    double hitRate,
    CacheWarmth warmth,
    int consecutiveMisses,
    boolean shouldAlert
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认的空缓存使用记录 */
    private static final CacheUsage EMPTY = new CacheUsage(0, 0, 0, 0.0, CacheWarmth.COLD, 0, false);

    /**
     * 返回空的缓存使用记录
     *
     * @return 空记录，所有字段为默认值
     */
    public static CacheUsage empty() {
        return EMPTY;
    }

    /**
     * 判断是否命中缓存
     *
     * @return 如果cachedTokens > 0返回true
     */
    public boolean hitCache() {
        return cachedTokens > 0;
    }

    /**
     * 创建带更新连续未命中次数的记录
     *
     * @param newConsecutiveMisses 新的连续未命中次数
     * @param maxMisses            告警阈值
     * @return 新的CacheUsage记录
     */
    public CacheUsage withConsecutiveMisses(int newConsecutiveMisses, int maxMisses) {
        return new CacheUsage(
            promptTokens,
            completionTokens,
            cachedTokens,
            hitRate,
            warmth,
            newConsecutiveMisses,
            newConsecutiveMisses >= maxMisses
        );
    }

    /**
     * 从Token使用量创建CacheUsage
     *
     * @param promptTokens     输入Token数
     * @param completionTokens 输出Token数
     * @param cachedTokens     缓存Token数（API不返回则传0）
     * @return CacheUsage记录
     */
    public static CacheUsage of(int promptTokens, int completionTokens, int cachedTokens) {
        double hitRate = promptTokens > 0 ? (double) cachedTokens / promptTokens : 0.0;
        CacheWarmth warmth = CacheWarmth.fromHitRate(hitRate);
        return new CacheUsage(promptTokens, completionTokens, cachedTokens, hitRate, warmth, 0, false);
    }
}
