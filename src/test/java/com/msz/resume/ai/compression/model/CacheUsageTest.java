package com.msz.resume.ai.chat.compression.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheUsage 单元测试
 */
class CacheUsageTest {

    @Test
    @DisplayName("empty() 返回默认空记录")
    void empty_shouldReturnDefaultRecord() {
        CacheUsage usage = CacheUsage.empty();

        assertEquals(0, usage.promptTokens());
        assertEquals(0, usage.completionTokens());
        assertEquals(0, usage.cachedTokens());
        assertEquals(0.0, usage.hitRate());
        assertEquals(CacheWarmth.COLD, usage.warmth());
        assertEquals(0, usage.consecutiveMisses());
        assertFalse(usage.shouldAlert());
    }

    @Test
    @DisplayName("hitCache() 缓存命中时返回true")
    void hitCache_whenCachedTokensGreaterThanZero_shouldReturnTrue() {
        CacheUsage usage = CacheUsage.of(1000, 500, 300);

        assertTrue(usage.hitCache());
    }

    @Test
    @DisplayName("hitCache() 缓存未命中时返回false")
    void hitCache_whenCachedTokensZero_shouldReturnFalse() {
        CacheUsage usage = CacheUsage.of(1000, 500, 0);

        assertFalse(usage.hitCache());
    }

    @Test
    @DisplayName("of() 正确计算命中率和热度")
    void of_shouldCalculateHitRateAndWarmth() {
        // 命中率 = 300/1000 = 0.3，应该是 WARM
        CacheUsage usage = CacheUsage.of(1000, 500, 300);

        assertEquals(1000, usage.promptTokens());
        assertEquals(500, usage.completionTokens());
        assertEquals(300, usage.cachedTokens());
        assertEquals(0.3, usage.hitRate(), 0.001);
        assertEquals(CacheWarmth.WARM, usage.warmth());
    }

    @Test
    @DisplayName("of() 命中率<30%时热度为COLD")
    void of_whenHitRateLessThan30_shouldBeCold() {
        CacheUsage usage = CacheUsage.of(1000, 500, 200);

        assertEquals(0.2, usage.hitRate(), 0.001);
        assertEquals(CacheWarmth.COLD, usage.warmth());
    }

    @Test
    @DisplayName("of() 命中率>70%时热度为HOT")
    void of_whenHitRateGreaterThan70_shouldBeHot() {
        CacheUsage usage = CacheUsage.of(1000, 500, 800);

        assertEquals(0.8, usage.hitRate(), 0.001);
        assertEquals(CacheWarmth.HOT, usage.warmth());
    }

    @Test
    @DisplayName("withConsecutiveMisses() 正确更新连续未命中次数")
    void withConsecutiveMisses_shouldUpdateCount() {
        CacheUsage usage = CacheUsage.of(1000, 500, 0);

        CacheUsage updated = usage.withConsecutiveMisses(5, 5);

        assertEquals(5, updated.consecutiveMisses());
        assertTrue(updated.shouldAlert());
    }

    @Test
    @DisplayName("withConsecutiveMisses() 未达阈值时不应告警")
    void withConsecutiveMisses_whenBelowThreshold_shouldNotAlert() {
        CacheUsage usage = CacheUsage.of(1000, 500, 0);

        CacheUsage updated = usage.withConsecutiveMisses(3, 5);

        assertEquals(3, updated.consecutiveMisses());
        assertFalse(updated.shouldAlert());
    }
}
