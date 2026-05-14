/**
 * 流量限制工具类
 *
 * 作用：基于 Redis 实现请求频率限制和封禁机制
 *
 * 核心方法：
 * - limitOnceCheck: 单次冷却限制（如验证码发送间隔）
 * - limitPeriodCheck: 周期性计数限制（如 API 请求频率）
 * - isBlocked: 检查是否被封禁
 */
package com.msz.resume.ai.auth.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class FlowUtils {

    private final StringRedisTemplate stringRedisTemplate;

    public FlowUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 单次冷却限制
     * 在冷却时间内只能请求一次（用于验证码发送限流）
     * @param key Redis Key
     * @param coldTime 冷却时间（秒）
     * @return true=允许请求，false=冷却中
     */
    public boolean limitOnceCheck(String key, int coldTime) {
        Boolean accepted = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", coldTime, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(accepted);
    }

    /**
     * 周期性计数限制
     * 在指定周期内请求次数超过阈值则封禁
     * @param counterKey 计数器 Key
     * @param blockKey 封禁 Key
     * @param blockTime 封禁时间（秒）
     * @param frequency 最大请求次数
     * @param period 统计周期（秒）
     * @return true=允许请求，false=超限被封禁
     */
    public boolean limitPeriodCheck(String counterKey, String blockKey, int blockTime, int frequency, int period) {
        // 1. 计数器 +1
        Long value = stringRedisTemplate.opsForValue().increment(counterKey);
        if (value == null) {
            return false;
        }
        // 2. 首次请求设置过期时间
        if (value == 1L) {
            stringRedisTemplate.expire(counterKey, period, TimeUnit.SECONDS);
        }
        // 3. 超限则封禁
        if (value > frequency) {
            stringRedisTemplate.opsForValue().set(blockKey, "", blockTime, TimeUnit.SECONDS);
            return false;
        }
        return true;
    }

    /** 检查 Key 是否存在（是否被封禁） */
    public boolean isBlocked(String blockKey) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(blockKey));
    }
}
