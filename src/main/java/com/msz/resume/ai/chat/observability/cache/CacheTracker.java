package com.msz.resume.ai.chat.observability.cache;

import com.msz.resume.ai.chat.compression.model.CacheUsage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * 缓存追踪器接口
 *
 * <p>从 LLM API 响应中提取缓存使用情况，计算命中率，追踪热度状态。
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
 * <p>注意：阿里云 DashScope API 可能不返回 cached_tokens 字段，此时 cachedTokens 默认为 0。
 */
public interface CacheTracker {

    /**
     * 从响应中提取缓存使用情况并更新内部状态
     *
     * <p>从 ChatResponse 的 TokenUsage 中提取 cachedTokens（若 API 不返回此字段则默认为 0），
     * 计算命中率，判定缓存热度，并追踪连续未命中次数。
     *
     * @param response LLM API 响应
     * @return 缓存使用情况，包含命中率、热度、告警信息
     */
    CacheUsage track(ChatResponse response);

    /**
     * 获取当前的缓存使用情况（最近一次 track 的结果）
     *
     * @return 当前缓存使用情况，未调用过 track 则返回 empty()
     */
    CacheUsage current();

    /**
     * 重置内部状态（新会话开始时调用）
     *
     * <p>清空连续未命中计数和当前缓存使用记录。
     */
    void reset();
}
