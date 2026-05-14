package com.msz.resume.ai.chat.observability.cache;

import com.msz.resume.ai.chat.compression.model.CacheUsage;
import com.msz.resume.ai.chat.compression.model.CacheWarmth;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认缓存追踪器实现
 *
 * <p>从 LangChain4j 的 ChatResponse 中提取 TokenUsage，计算缓存命中率和热度状态。
 *
 * <p>实现细节：
 * <ul>
 *   <li>使用 AtomicInteger 保证线程安全的连续未命中计数</li>
 *   <li>阿里云 DashScope API 可能不返回 cached_tokens，此时默认为 0</li>
 *   <li>命中判定：cachedTokens > 0</li>
 * </ul>
 */
@Slf4j
@Component
public class DefaultCacheTracker implements CacheTracker {

    private final JarvisCachingProperties properties;

    /** 当前缓存使用情况（volatile保证可见性） */
    private volatile CacheUsage currentUsage = CacheUsage.empty();

    /** 连续未命中次数（AtomicInteger保证原子性） */
    private final AtomicInteger consecutiveMisses = new AtomicInteger(0);

    public DefaultCacheTracker(JarvisCachingProperties properties) {
        this.properties = properties;
    }

    @Override
    public CacheUsage track(ChatResponse response) {
        if (response == null) {
            log.warn("[CacheTracker] response为null，跳过追踪");
            return CacheUsage.empty();
        }

        // 提取Token使用量
        int promptTokens = 0;
        int completionTokens = 0;
        int cachedTokens = 0;

        if (response.tokenUsage() != null) {
            var tokenUsage = response.tokenUsage();

            // 输入Token
            promptTokens = tokenUsage.inputTokenCount() != null
                    ? tokenUsage.inputTokenCount()
                    : 0;

            // 输出Token
            completionTokens = tokenUsage.outputTokenCount() != null
                    ? tokenUsage.outputTokenCount()
                    : 0;

            // 缓存Token - 尝试多种方式提取
            cachedTokens = extractCachedTokens(tokenUsage);
        }

        // 计算命中率
        double hitRate = promptTokens > 0 ? (double) cachedTokens / promptTokens : 0.0;

        // 判定热度
        CacheWarmth warmth = CacheWarmth.fromHitRate(hitRate);

        // 更新连续未命中计数
        int misses = updateConsecutiveMisses(cachedTokens);

        // 创建CacheUsage记录
        currentUsage = new CacheUsage(
                promptTokens,
                completionTokens,
                cachedTokens,
                hitRate,
                warmth,
                misses,
                misses >= properties.getMaxConsecutiveMisses()
        );

        // 日志输出
        logUsage(currentUsage);

        return currentUsage;
    }

    @Override
    public CacheUsage current() {
        return currentUsage;
    }

    @Override
    public void reset() {
        currentUsage = CacheUsage.empty();
        consecutiveMisses.set(0);
        log.debug("[CacheTracker] 状态已重置");
    }

    /**
     * 尝试从TokenUsage中提取cachedTokens
     *
     * <p>不同API提供商返回此字段的方式不同：
     * <ul>
     *   <li>OpenAI: 通过 OpenAiTokenUsage 的 cachedTokens 字段</li>
     *   <li>阿里云DashScope: 可能不返回此字段</li>
     * </ul>
     *
     * @param tokenUsage Token使用量对象
     * @return 缓存Token数，无法提取则返回0
     */
    private int extractCachedTokens(Object tokenUsage) {
        log.debug("[CacheTracker] 尝试从 TokenUsage 提取 cachedTokens, 实际类型: {}", tokenUsage.getClass().getName());

        // 尝试反射获取 cachedTokens 字段（兼容不同实现）
        try {
            var method = tokenUsage.getClass().getMethod("cachedTokens");
            if (method.getReturnType() == Integer.class || method.getReturnType() == int.class) {
                Object result = method.invoke(tokenUsage);
                if (result instanceof Integer integer) {
                    log.debug("[CacheTracker] 直接通过 cachedTokens() 提取到: {}", integer);
                    return integer;
                }
            }
        } catch (NoSuchMethodException e) {
            log.debug("[CacheTracker] TokenUsage 没有 cachedTokens() 方法");
        } catch (Exception e) {
            log.debug("[CacheTracker] 调用 cachedTokens() 失败: {}", e.getMessage());
        }

        // 尝试其他可能的字段名
        try {
            var field = tokenUsage.getClass().getDeclaredField("cacheReadInputTokens");
            field.setAccessible(true);
            Object result = field.get(tokenUsage);
            if (result instanceof Integer integer) {
                log.debug("[CacheTracker] 通过 cacheReadInputTokens 字段提取到: {}", integer);
                return integer;
            }
        } catch (NoSuchFieldException e) {
            log.debug("[CacheTracker] TokenUsage 没有 cacheReadInputTokens 字段");
        } catch (Exception e) {
            log.debug("[CacheTracker] 访问 cacheReadInputTokens 字段失败: {}", e.getMessage());
        }

        // 策略3: 嵌套结构 inputTokensDetails().cachedTokens()
        // OpenAI/智谱AI 兼容 API 返回: {"prompt_tokens_details":{"cached_tokens":1414}}
        // 对应 langchain4j OpenAiTokenUsage: tokenUsage.inputTokensDetails().cachedTokens()
        try {
            var detailsMethod = tokenUsage.getClass().getMethod("inputTokensDetails");
            Object details = detailsMethod.invoke(tokenUsage);
            log.debug("[CacheTracker] inputTokensDetails() 返回: {}, 类型: {}", details, details != null ? details.getClass().getName() : "null");
            if (details != null) {
                var cachedMethod = details.getClass().getMethod("cachedTokens");
                Object result = cachedMethod.invoke(details);
                log.debug("[CacheTracker] cachedTokens() 返回: {}, 类型: {}", result, result != null ? result.getClass().getName() : "null");
                if (result instanceof Integer val && val > 0) {
                    log.info("[CacheTracker] 成功通过 inputTokensDetails().cachedTokens() 提取到: {}", val);
                    return val;
                } else if (result == null) {
                    log.debug("[CacheTracker] cachedTokens() 返回 null，可能API未返回缓存信息");
                } else if (result instanceof Integer val && val == 0) {
                    log.debug("[CacheTracker] cachedTokens() 返回 0，无缓存命中");
                }
            }
        } catch (NoSuchMethodException e) {
            log.debug("[CacheTracker] TokenUsage 没有 inputTokensDetails() 方法: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("[CacheTracker] 调用 inputTokensDetails().cachedTokens() 失败: {}", e.getMessage());
        }

        // 无法提取，返回0
        log.debug("[CacheTracker] 无法从 TokenUsage 提取 cachedTokens，返回 0");
        return 0;
    }

    /**
     * 更新连续未命中计数
     *
     * @param cachedTokens 缓存Token数
     * @return 更新后的连续未命中次数
     */
    private int updateConsecutiveMisses(int cachedTokens) {
        if (cachedTokens > 0) {
            // 缓存命中，重置计数
            consecutiveMisses.set(0);
            return 0;
        } else {
            // 缓存未命中，计数+1
            return consecutiveMisses.incrementAndGet();
        }
    }

    /**
     * 输出缓存使用日志
     */
    private void logUsage(CacheUsage usage) {
        if (!properties.isLogDetails()) {
            return;
        }

        // 基础日志
        log.info("[CacheTracker] 缓存: {}%, 热度: {}",
                String.format("%.1f", usage.hitRate() * 100),
                usage.warmth().getLabel());

        // Token详情
        log.debug("[CacheTracker] promptTokens={}, completionTokens={}, cachedTokens={}",
                usage.promptTokens(),
                usage.completionTokens(),
                usage.cachedTokens());

        // 告警日志
        if (usage.shouldAlert()) {
            log.warn("[CacheTracker] 缓存连续未命中 {} 次，可能需要检查提示词稳定性",
                    usage.consecutiveMisses());
        }
    }
}
