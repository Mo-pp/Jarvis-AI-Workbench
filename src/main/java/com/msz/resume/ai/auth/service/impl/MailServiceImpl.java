/**
 * 邮件服务实现类
 *
 * 作用：实现验证码的生成、存储、验证、限流
 *
 * 核心流程：
 * 1. 发送验证码：
 *    - 检查发送频率（60秒间隔）
 *    - 生成6位随机验证码
 *    - 存入 Redis（3分钟有效）
 *    - 发送到 RabbitMQ 队列异步发送邮件
 * 2. 验证验证码：
 *    - 从 Redis 读取验证码比对
 * 3. 删除验证码：
 *    - 验证成功后删除
 */
package com.msz.resume.ai.auth.service.impl;

import com.msz.resume.ai.auth.Const;
import com.msz.resume.ai.auth.service.MailService;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class MailServiceImpl implements MailService {

    private final AmqpTemplate amqpTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 验证码发送间隔（秒），默认60秒 */
    @Value("${spring.mail.code-interval:60}")
    private int codeInterval;

    public MailServiceImpl(AmqpTemplate amqpTemplate, StringRedisTemplate stringRedisTemplate) {
        this.amqpTemplate = amqpTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 发送验证码
     * 流程：限流检查 → 生成验证码 → 存入 Redis → 发送到队列
     */
    @Override
    public String sendVerificationCode(String type, String email) {
        // 1. 限流检查：同一邮箱同一类型 60 秒内只能发送一次
        String limitKey = buildLimitKey(type, email);
        Boolean accepted = stringRedisTemplate.opsForValue()
                .setIfAbsent(limitKey, "", codeInterval, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(accepted)) {
            return "请求过于频繁，请稍后再试";
        }

        // 2. 生成 6 位随机验证码
        int code = secureRandom.nextInt(900000) + 100000;

        // 3. 发送到 RabbitMQ 队列（异步发送邮件）
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("email", email);
        data.put("code", code);
        amqpTemplate.convertAndSend("mail", data);

        // 4. 存入 Redis，有效期 3 分钟
        stringRedisTemplate.opsForValue()
                .set(buildCodeKey(type, email), String.valueOf(code), 3, TimeUnit.MINUTES);
        return null;
    }

    /**
     * 验证验证码
     * 从 Redis 读取并比对
     */
    @Override
    public String verifyCode(String type, String email, String code) {
        String storedCode = stringRedisTemplate.opsForValue().get(buildCodeKey(type, email));
        if (storedCode == null) {
            return "验证码已过期，请重新获取";
        }
        if (!storedCode.equals(code)) {
            return "验证码错误";
        }
        return null;
    }

    /** 删除验证码（验证成功后调用） */
    @Override
    public void deleteCode(String type, String email) {
        stringRedisTemplate.delete(buildCodeKey(type, email));
    }

    /** 构建验证码存储 Key：verify:email:data:{type}:{email} */
    private String buildCodeKey(String type, String email) {
        return Const.VERIFY_EMAIL_DATA + type + ":" + email;
    }

    /** 构建限流 Key：verify:email:limit:{type}:{email} */
    private String buildLimitKey(String type, String email) {
        return Const.VERIFY_EMAIL_LIMIT + type + ":" + email;
    }
}
