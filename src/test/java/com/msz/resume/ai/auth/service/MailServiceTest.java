package com.msz.resume.ai.auth.service;

import com.msz.resume.ai.auth.Const;
import com.msz.resume.ai.auth.service.impl.MailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailServiceTest {

    private MailServiceImpl mailService;
    private AmqpTemplate amqpTemplate;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        amqpTemplate = Mockito.mock(AmqpTemplate.class);
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);

        mailService = new MailServiceImpl(amqpTemplate, redisTemplate);
        ReflectionTestUtils.setField(mailService, "codeInterval", 60);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("发送验证码 - 成功")
    void testSendVerificationCode_Success() {
        when(valueOps.setIfAbsent(anyString(), eq(""), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);

        String result = mailService.sendVerificationCode("register", "test@example.com");

        assertNull(result);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(amqpTemplate).convertAndSend(eq("mail"), captor.capture());
        assertEquals("register", captor.getValue().get("type"));
        assertEquals("test@example.com", captor.getValue().get("email"));
        verify(valueOps).set(eq(Const.VERIFY_EMAIL_DATA + "register:test@example.com"), anyString(), eq(3L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("发送验证码 - 限流中")
    void testSendVerificationCode_RateLimited() {
        when(valueOps.setIfAbsent(anyString(), eq(""), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(false);

        String result = mailService.sendVerificationCode("register", "test@example.com");

        assertEquals("请求过于频繁，请稍后再试", result);
        verify(amqpTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("校验验证码 - 成功")
    void testVerifyCode_Success() {
        when(valueOps.get(Const.VERIFY_EMAIL_DATA + "register:test@example.com")).thenReturn("123456");
        assertNull(mailService.verifyCode("register", "test@example.com", "123456"));
    }

    @Test
    @DisplayName("校验验证码 - 错误")
    void testVerifyCode_WrongCode() {
        when(valueOps.get(Const.VERIFY_EMAIL_DATA + "register:test@example.com")).thenReturn("123456");
        assertEquals("验证码错误", mailService.verifyCode("register", "test@example.com", "654321"));
    }

    @Test
    @DisplayName("删除验证码")
    void testDeleteCode() {
        mailService.deleteCode("register", "test@example.com");
        verify(redisTemplate).delete(Const.VERIFY_EMAIL_DATA + "register:test@example.com");
    }
}
