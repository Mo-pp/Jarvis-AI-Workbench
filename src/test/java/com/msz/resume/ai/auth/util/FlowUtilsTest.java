package com.msz.resume.ai.auth.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowUtilsTest {

    private FlowUtils flowUtils;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        flowUtils = new FlowUtils(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("单次限流 - 首次通过")
    void testLimitOnceCheck_FirstTime() {
        when(valueOps.setIfAbsent(eq("test:limit"), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
        assertTrue(flowUtils.limitOnceCheck("test:limit", 60));
    }

    @Test
    @DisplayName("单次限流 - 冷却期内拒绝")
    void testLimitOnceCheck_InCoolDown() {
        when(valueOps.setIfAbsent(eq("test:limit"), eq("1"), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(false);
        assertFalse(flowUtils.limitOnceCheck("test:limit", 60));
    }

    @Test
    @DisplayName("周期限流 - 未超限通过")
    void testLimitPeriodCheck_WithinLimit() {
        when(valueOps.increment("test:counter")).thenReturn(50L);
        assertTrue(flowUtils.limitPeriodCheck("test:counter", "test:block", 30, 200, 3));
    }

    @Test
    @DisplayName("周期限流 - 超限封禁")
    void testLimitPeriodCheck_ExceedLimit() {
        when(valueOps.increment("test:counter")).thenReturn(201L);
        assertFalse(flowUtils.limitPeriodCheck("test:counter", "test:block", 30, 200, 3));
        verify(valueOps).set(eq("test:block"), eq(""), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("检查是否封禁")
    void testIsBlocked() {
        when(redisTemplate.hasKey("test:block")).thenReturn(true, false);
        assertTrue(flowUtils.isBlocked("test:block"));
        assertFalse(flowUtils.isBlocked("test:block"));
    }
}
