/**
 * JWT 工具类
 *
 * 作用：JWT Token 的生成、解析、验证和黑名单管理
 *
 * 核心流程：
 * 1. 登录成功时调用 createToken() 生成 JWT
 * 2. 请求到达时 JwtAuthenticationFilter 调用 resolveToken() 验证 Token
 * 3. 退出登录时调用 invalidateToken() 将 Token 加入黑名单
 */
package com.msz.resume.ai.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JwtUtils {

    /** JWT 签名密钥，从配置文件读取 */
    @Value("${spring.security.key}")
    private String key;

    /** Token 默认过期时间（小时） */
    @Value("${spring.security.expire}")
    private int expire;

    private final StringRedisTemplate stringRedisTemplate;

    public JwtUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** 使用默认过期时间创建 Token */
    public String createToken(UserDetails user, String username, Integer userId) {
        return createToken(user, username, userId, expire);
    }

    /**
     * 创建 JWT Token
     * Token 包含：用户ID、用户名、权限列表、过期时间、签发时间、唯一ID（用于黑名单）
     */
    public String createToken(UserDetails user, String username, Integer userId, int expireHours) {
        Algorithm algorithm = Algorithm.HMAC256(key);
        Date now = new Date();
        Date expireAt = getExpireTime(expireHours);
        return JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("id", userId)
                .withClaim("name", username)
                .withClaim("authorities", user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .withExpiresAt(expireAt)
                .withIssuedAt(now)
                .sign(algorithm);
    }

    /**
     * 解析并验证 Token
     * 验证步骤：1.提取Token 2.验证签名 3.检查过期 4.检查黑名单
     * @return 验证成功返回 DecodedJWT，失败返回 null
     */
    public DecodedJWT resolveToken(String headerToken) {
        String token = convertToken(headerToken);
        if (token == null) {
            return null;
        }

        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(key)).build().verify(token);
            if (isInvalidToken(jwt.getId())) {
                return null;
            }
            return jwt;
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    /**
     * 使 Token 失效（退出登录时调用）
     * 将 Token 的唯一ID存入 Redis 黑名单，有效期等于 Token 剩余时间
     * @return 成功返回 true，Token 无效返回 false
     */
    public boolean invalidateToken(String headerToken) {
        String token = convertToken(headerToken);
        if (token == null) {
            return false;
        }

        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(key)).build().verify(token);
            return addToBlacklist(jwt.getId(), jwt.getExpiresAt());
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    /** 从 JWT 中提取用户信息，构建 Spring Security 的 UserDetails 对象 */
    public UserDetails toUser(DecodedJWT jwt) {
        Map<String, Claim> claims = jwt.getClaims();
        return User.builder()
                .username(claims.get("name").asString())
                .password("******")
                .authorities(claims.get("authorities").asArray(String.class))
                .build();
    }

    /** 从 JWT 中提取用户ID */
    public Integer toUserId(DecodedJWT jwt) {
        return jwt.getClaim("id").asInt();
    }

    /** 获取默认配置的过期时间 */
    public Date getExpireTime() {
        return getExpireTime(expire);
    }

    /** 计算指定小时数后的过期时间 */
    public Date getExpireTime(int expireHours) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, expireHours);
        return calendar.getTime();
    }

    /** 获取默认过期时间（小时） */
    public int getExpireHours() {
        return expire;
    }

    /**
     * 从 Authorization Header 提取 Token
     * 输入格式: "Bearer eyJhbGci..."
     * 输出: "eyJhbGci..."
     */
    private String convertToken(String headerToken) {
        if (headerToken == null || !headerToken.startsWith("Bearer ")) {
            return null;
        }
        return headerToken.substring(7);
    }

    /** 检查 Token 是否在黑名单中（已退出登录） */
    private boolean isInvalidToken(String jwtId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(Const.JWT_BLACK_LIST + jwtId));
    }

    /**
     * 将 Token 加入黑名单
     * 黑名单有效期 = Token 剩余有效期（过期后自动删除，节省 Redis 内存）
     */
    private boolean addToBlacklist(String jwtId, Date expiresAt) {
        if (isInvalidToken(jwtId)) {
            return false;
        }

        long remainingMs = Math.max(expiresAt.getTime() - System.currentTimeMillis(), 0);
        long remainingSeconds = Math.max(TimeUnit.MILLISECONDS.toSeconds(remainingMs), 1);
        stringRedisTemplate.opsForValue()
                .set(Const.JWT_BLACK_LIST + jwtId, "", remainingSeconds, TimeUnit.SECONDS);
        return true;
    }
}
