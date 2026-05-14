package com.msz.resume.ai.chat.observability.cache;

import com.msz.resume.ai.chat.compression.model.CacheUsage;
import com.msz.resume.ai.chat.compression.model.CacheWarmth;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultCacheTracker 单元测试
 *
 * <p>使用 ChatResponse.builder() 创建测试对象，
 * 避免 Mockito 在 Java 21 上的兼容性问题。
 */
class DefaultCacheTrackerTest {

    private JarvisCachingProperties properties;
    private DefaultCacheTracker cacheTracker;

    @BeforeEach
    void setUp() {
        properties = new JarvisCachingProperties();
        properties.setMaxConsecutiveMisses(5);
        properties.setLogDetails(false); // 禁用日志避免干扰测试
        cacheTracker = new DefaultCacheTracker(properties);
    }

    @Test
    @DisplayName("track() response为null时返回empty")
    void track_whenResponseIsNull_shouldReturnEmpty() {
        CacheUsage usage = cacheTracker.track(null);

        assertEquals(CacheUsage.empty(), usage);
    }

    @Test
    @DisplayName("track() tokenUsage为null时返回默认值")
    void track_whenTokenUsageIsNull_shouldReturnDefaultValues() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("test"))
                .tokenUsage(null)
                .build();

        CacheUsage usage = cacheTracker.track(response);

        assertEquals(0, usage.promptTokens());
        assertEquals(0, usage.completionTokens());
        assertEquals(0, usage.cachedTokens());
        assertEquals(0.0, usage.hitRate());
        assertEquals(CacheWarmth.COLD, usage.warmth());
    }

    @Test
    @DisplayName("track() 正确提取Token使用量（无缓存命中）")
    void track_shouldExtractTokenUsageWithoutCache() {
        ChatResponse response = createResponse(1000, 500);
        CacheUsage usage = cacheTracker.track(response);

        assertEquals(1000, usage.promptTokens());
        assertEquals(500, usage.completionTokens());
        // DashScope 不返回 cachedTokens，应为 0
        assertEquals(0, usage.cachedTokens());
        assertEquals(0.0, usage.hitRate(), 0.001);
        assertEquals(CacheWarmth.COLD, usage.warmth());
    }

    @Test
    @DisplayName("track() 连续未命中计数正确递增")
    void track_consecutiveMissesShouldIncrement() {
        // 第一次未命中
        CacheUsage usage1 = cacheTracker.track(createResponse(1000, 500));
        assertEquals(1, usage1.consecutiveMisses());

        // 第二次未命中
        CacheUsage usage2 = cacheTracker.track(createResponse(1000, 500));
        assertEquals(2, usage2.consecutiveMisses());

        // 第三次未命中
        CacheUsage usage3 = cacheTracker.track(createResponse(1000, 500));
        assertEquals(3, usage3.consecutiveMisses());
    }

    @Test
    @DisplayName("track() 连续未命中达到阈值时触发告警")
    void track_whenConsecutiveMissesReachThreshold_shouldAlert() {
        // 连续触发4次未命中（未达到阈值5）
        for (int i = 0; i < 4; i++) {
            CacheUsage result = cacheTracker.track(createResponse(1000, 500));
            assertEquals(i + 1, result.consecutiveMisses());
            assertFalse(result.shouldAlert());
        }

        // 第5次应该触发告警
        CacheUsage fifthResult = cacheTracker.track(createResponse(1000, 500));

        assertEquals(5, fifthResult.consecutiveMisses());
        assertTrue(fifthResult.shouldAlert());
    }

    @Test
    @DisplayName("current() 返回最近一次追踪结果")
    void current_shouldReturnLatestUsage() {
        cacheTracker.track(createResponse(1000, 500));

        CacheUsage current = cacheTracker.current();

        assertEquals(1000, current.promptTokens());
        assertEquals(500, current.completionTokens());
    }

    @Test
    @DisplayName("reset() 重置所有状态")
    void reset_shouldClearAllState() {
        // 先触发一些状态
        cacheTracker.track(createResponse(1000, 500));
        cacheTracker.track(createResponse(1000, 500));

        // 重置
        cacheTracker.reset();

        // 验证状态已重置
        CacheUsage current = cacheTracker.current();
        assertEquals(0, current.consecutiveMisses());
        assertEquals(0, current.promptTokens());
    }

    @Test
    @DisplayName("reset() 后连续未命中计数重新开始")
    void reset_shouldResetConsecutiveMisses() {
        // 触发几次未命中
        for (int i = 0; i < 3; i++) {
            cacheTracker.track(createResponse(1000, 500));
        }

        // 重置
        cacheTracker.reset();

        // 再次触发未命中，计数应从1开始
        CacheUsage usage = cacheTracker.track(createResponse(1000, 500));

        assertEquals(1, usage.consecutiveMisses());
    }

    @Test
    @DisplayName("hitCache() 当cachedTokens为0时返回false")
    void hitCache_whenZeroCachedTokens_shouldReturnFalse() {
        CacheUsage usage = cacheTracker.track(createResponse(1000, 500));

        assertFalse(usage.hitCache());
    }

    @Test
    @DisplayName("track() 正确提取OpenAiTokenUsage的cachedTokens")
    void track_shouldExtractCachedTokensFromOpenAiTokenUsage() {
        // 创建带有 cachedTokens 的 OpenAiTokenUsage
        OpenAiTokenUsage tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(12953)
                .outputTokenCount(750)
                .totalTokenCount(13703)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(12365)
                        .build())
                .build();

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("test response"))
                .tokenUsage(tokenUsage)
                .build();

        CacheUsage usage = cacheTracker.track(response);

        // 验证正确提取了 cachedTokens
        assertEquals(12953, usage.promptTokens());
        assertEquals(750, usage.completionTokens());
        assertEquals(12365, usage.cachedTokens());
        // 命中率 = 12365 / 12953 ≈ 95.5%
        assertEquals(0.955, usage.hitRate(), 0.01);
        assertTrue(usage.hitCache());
        // 缓存命中后，连续未命中计数应重置为0
        assertEquals(0, usage.consecutiveMisses());
    }

    @Test
    @DisplayName("track() OpenAiTokenUsage无cachedTokens时返回0")
    void track_whenOpenAiTokenUsageHasNoCachedTokens_shouldReturnZero() {
        OpenAiTokenUsage tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(1000)
                .outputTokenCount(500)
                .totalTokenCount(1500)
                // 不设置 inputTokensDetails
                .build();

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("test response"))
                .tokenUsage(tokenUsage)
                .build();

        CacheUsage usage = cacheTracker.track(response);

        assertEquals(1000, usage.promptTokens());
        assertEquals(0, usage.cachedTokens());
        assertEquals(0.0, usage.hitRate(), 0.001);
    }

    @Test
    @DisplayName("track() 缓存命中后连续未命中计数重置")
    void track_whenCacheHit_shouldResetConsecutiveMisses() {
        // 先触发几次未命中
        cacheTracker.track(createResponse(1000, 500));
        cacheTracker.track(createResponse(1000, 500));
        assertEquals(2, cacheTracker.current().consecutiveMisses());

        // 然后命中缓存
        OpenAiTokenUsage tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(1000)
                .outputTokenCount(500)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(800)
                        .build())
                .build();

        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("test response"))
                .tokenUsage(tokenUsage)
                .build();

        CacheUsage usage = cacheTracker.track(response);

        // 连续未命中计数应重置为0
        assertEquals(0, usage.consecutiveMisses());
        assertEquals(800, usage.cachedTokens());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的 ChatResponse
     */
    private ChatResponse createResponse(int inputTokens, int outputTokens) {
        TokenUsage tokenUsage = new TokenUsage(inputTokens, outputTokens);
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("test response"))
                .tokenUsage(tokenUsage)
                .build();
    }
}
