/**
 * 认证模块常量定义
 *
 * 作用：集中管理 Redis Key 前缀和请求属性名，避免硬编码
 *
 * 包含：
 * - JWT 黑名单 Key 前缀
 * - 验证码存储/限流 Key 前缀
 * - 流量限制 Key 前缀
 * - 请求属性名
 */
package com.msz.resume.ai.auth;

/** 常量类，禁止实例化 */
public final class Const {

    private Const() {}

    /** Redis JWT 黑名单前缀，格式: jwt:blacklist:{jwtId} */
    public static final String JWT_BLACK_LIST = "jwt:blacklist:";

    /** 验证码 Redis 前缀，格式: verify:email:data:{type}:{email} */
    public static final String VERIFY_EMAIL_DATA = "verify:email:data:";

    /** 验证码发送限流 Redis 前缀，格式: verify:email:limit:{type}:{email} */
    public static final String VERIFY_EMAIL_LIMIT = "verify:email:limit:";

    /** 请求属性：用户ID，JwtAuthenticationFilter 验证成功后存入 request */
    public static final String ATTR_USER_ID = "userId";

    /** 限流计数器 Redis 前缀，格式: flow:counter:{ip} */
    public static final String FLOW_LIMIT_COUNTER = "flow:counter:";

    /** 限流封禁 Redis 前缀，格式: flow:block:{ip} */
    public static final String FLOW_LIMIT_BLOCK = "flow:block:";
}
