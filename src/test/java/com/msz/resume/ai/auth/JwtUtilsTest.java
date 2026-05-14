/**
 * JwtUtils 单元测试
 */
package com.msz.resume.ai.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JwtUtils 单元测试
 */
class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        jwtUtils = new JwtUtils(redisTemplate);

        // 设置私有字段
        ReflectionTestUtils.setField(jwtUtils, "key", "test-secret-key-must-be-at-least-256-bits-long-for-hs256");
        ReflectionTestUtils.setField(jwtUtils, "expire", 168);
    }

    @Test
    @DisplayName("创建 Token - 成功")
    void testCreateToken_Success() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1);

        assertNotNull(token);
        assertTrue(token.startsWith("ey"));  // JWT 以 ey 开头
    }

    @Test
    @DisplayName("创建 Token（指定有效期）- 成功")
    void testCreateToken_WithCustomExpire() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1, 720);  // 30 天

        assertNotNull(token);
    }

    @Test
    @DisplayName("解析 Token - 有效 Token")
    void testResolveToken_Valid() {
        // 准备
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1);
        String headerToken = "Bearer " + token;

        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // 执行
        DecodedJWT jwt = jwtUtils.resolveToken(headerToken);

        // 验证
        assertNotNull(jwt);
        assertEquals("john_doe", jwt.getClaim("name").asString());
        assertEquals(1, jwt.getClaim("id").asInt());
    }

    @Test
    @DisplayName("解析 Token - 无 Bearer 前缀返回 null")
    void testResolveToken_NoBearerPrefix() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1);

        DecodedJWT jwt = jwtUtils.resolveToken(token);  // 没有 Bearer 前缀

        assertNull(jwt);
    }

    @Test
    @DisplayName("解析 Token - null 参数返回 null")
    void testResolveToken_Null() {
        DecodedJWT jwt = jwtUtils.resolveToken(null);
        assertNull(jwt);
    }

    @Test
    @DisplayName("解析 Token - 无效 Token 返回 null")
    void testResolveToken_Invalid() {
        DecodedJWT jwt = jwtUtils.resolveToken("Bearer invalid.token.here");
        assertNull(jwt);
    }

    @Test
    @DisplayName("从 JWT 获取用户ID")
    void testToUserId() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 123);
        String headerToken = "Bearer " + token;

        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        DecodedJWT jwt = jwtUtils.resolveToken(headerToken);
        Integer userId = jwtUtils.toUserId(jwt);

        assertEquals(123, userId);
    }

    @Test
    @DisplayName("从 JWT 构建 UserDetails")
    void testToUser() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1);
        String headerToken = "Bearer " + token;

        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        DecodedJWT jwt = jwtUtils.resolveToken(headerToken);
        UserDetails result = jwtUtils.toUser(jwt);

        assertEquals("john_doe", result.getUsername());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    @DisplayName("Token 在黑名单中返回 null")
    void testResolveToken_InBlacklist() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1);
        String headerToken = "Bearer " + token;

        // 模拟 Token 在黑名单中
        when(redisTemplate.hasKey(startsWith(Const.JWT_BLACK_LIST))).thenReturn(true);

        DecodedJWT jwt = jwtUtils.resolveToken(headerToken);

        assertNull(jwt);
    }

    @Test
    @DisplayName("使 Token 失效（加入黑名单）- 成功")
    void testInvalidateToken_Success() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1);
        String headerToken = "Bearer " + token;

        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Mock opsForValue
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        boolean result = jwtUtils.invalidateToken(headerToken);

        assertTrue(result);
        verify(valueOps).set(anyString(), eq(""), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("使 Token 失效 - 重复失效返回 false")
    void testInvalidateToken_Twice() {
        UserDetails user = User.builder()
                .username("john_doe")
                .password("hashed_password")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        String token = jwtUtils.createToken(user, "john_doe", 1);
        String headerToken = "Bearer " + token;

        // Mock opsForValue
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 第一次不在黑名单
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        boolean first = jwtUtils.invalidateToken(headerToken);
        assertTrue(first);

        // 第二次已在黑名单
        when(redisTemplate.hasKey(startsWith(Const.JWT_BLACK_LIST))).thenReturn(true);
        boolean second = jwtUtils.invalidateToken(headerToken);
        assertFalse(second);
    }

    @Test
    @DisplayName("使 Token 失效 - 无效 Token 返回 false")
    void testInvalidateToken_Invalid() {
        boolean result = jwtUtils.invalidateToken("Bearer invalid.token");
        assertFalse(result);
    }

    @Test
    @DisplayName("使 Token 失效 - null 返回 false")
    void testInvalidateToken_Null() {
        boolean result = jwtUtils.invalidateToken(null);
        assertFalse(result);
    }

    @Test
    @DisplayName("获取过期时间")
    void testGetExpireTime() {
        assertNotNull(jwtUtils.getExpireTime());
    }

    @Test
    @DisplayName("获取配置的有效期")
    void testGetExpireHours() {
        assertEquals(168, jwtUtils.getExpireHours());
    }
}
