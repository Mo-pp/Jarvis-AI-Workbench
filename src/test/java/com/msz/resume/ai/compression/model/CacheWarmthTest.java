package com.msz.resume.ai.chat.compression.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CacheWarmth 单元测试
 */
class CacheWarmthTest {

    @ParameterizedTest
    @CsvSource({
        "0.0, COLD",
        "0.1, COLD",
        "0.29, COLD",
        "0.3, WARM",
        "0.5, WARM",
        "0.7, WARM",
        "0.71, HOT",
        "0.9, HOT",
        "1.0, HOT"
    })
    @DisplayName("fromHitRate() 根据命中率返回正确的热度")
    void fromHitRate_shouldReturnCorrectWarmth(double hitRate, CacheWarmth expected) {
        CacheWarmth warmth = CacheWarmth.fromHitRate(hitRate);

        assertEquals(expected, warmth);
    }

    @Test
    @DisplayName("COLD 标签和描述正确")
    void cold_shouldHaveCorrectLabelAndDescription() {
        assertEquals("冷", CacheWarmth.COLD.getLabel());
        assertEquals("命中率低于30%，缓存效率低", CacheWarmth.COLD.getDescription());
    }

    @Test
    @DisplayName("WARM 标签和描述正确")
    void warm_shouldHaveCorrectLabelAndDescription() {
        assertEquals("温", CacheWarmth.WARM.getLabel());
        assertEquals("命中率30%~70%，缓存效率一般", CacheWarmth.WARM.getDescription());
    }

    @Test
    @DisplayName("HOT 标签和描述正确")
    void hot_shouldHaveCorrectLabelAndDescription() {
        assertEquals("热", CacheWarmth.HOT.getLabel());
        assertEquals("命中率高于70%，缓存效率高", CacheWarmth.HOT.getDescription());
    }
}
