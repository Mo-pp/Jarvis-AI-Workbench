package com.msz.resume.ai.hook.impl;

import com.msz.resume.ai.hook.HookContext;
import com.msz.resume.ai.hook.HookResult;
import com.msz.resume.ai.hook.ToolHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于会话ID的滑动窗口限流 Hook
 *
 * <p>限制单个会话在指定时间窗口内的工具调用次数。
 * 超过阈值时返回 block，附带"调用过于频繁"的提示。
 *
 * <p>配置通过 application.yml：
 * <pre>
 * jarvis:
 *   hooks:
 *     rate-limit:
 *       max-calls: 30
 *       window-seconds: 60
 * </pre>
 *
 * <p>在 YAML 配置中注册为 PreToolUse Hook：
 * <pre>
 * pre_tool_use:
 *   - name: rate_limit
 *     matcher: ".*"
 *     action: rateLimitHook
 *     priority: 50
 * </pre>
 */
@Slf4j
@Component("rateLimitHook")
public class RateLimitHook implements ToolHook {

    /** 默认：60秒内最多30次调用 */
    private static final int DEFAULT_MAX_CALLS = 30;
    private static final int DEFAULT_WINDOW_SECONDS = 60;

    /** 会话ID → 限流窗口 */
    private final ConcurrentHashMap<String, RateLimitWindow> windows = new ConcurrentHashMap<>();

    private final int maxCalls;
    private final int windowSeconds;

    public RateLimitHook() {
        this(DEFAULT_MAX_CALLS, DEFAULT_WINDOW_SECONDS);
    }

    /**
     * 用于测试或自定义配置的构造函数
     */
    public RateLimitHook(int maxCalls, int windowSeconds) {
        this.maxCalls = maxCalls;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public HookResult preToolUse(HookContext context) {
        String sessionId = context.sessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            // 无会话ID，放行
            return HookResult.continueResult();
        }

        RateLimitWindow window = windows.computeIfAbsent(sessionId,
                k -> new RateLimitWindow(maxCalls, windowSeconds));

        if (window.tryAcquire()) {
            return HookResult.continueResult();
        }

        log.warn("[RateLimitHook] 会话 {} 工具调用频率超限（{}/{}秒内最多{}次）",
                sessionId, context.toolName(), windowSeconds, maxCalls);

        return HookResult.block(String.format(
                "工具调用频率超限：会话在%d秒内最多可调用%d次。请稍等片刻再试。",
                windowSeconds, maxCalls));
    }

    @Override
    public String postToolUse(HookContext context, String toolResult) {
        // 限流是 PreToolUse 行为，PostToolUse 无需处理
        return toolResult;
    }

    /**
     * 滑动窗口限流器
     *
     * <p>使用简化的固定窗口计数器（而非真正的滑动窗口），
     * 在窗口结束时重置计数。对于工具调用限流场景已足够精确。
     */
    static class RateLimitWindow {
        private final int maxCalls;
        private final int windowSeconds;
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long windowStartMs;

        RateLimitWindow(int maxCalls, int windowSeconds) {
            this.maxCalls = maxCalls;
            this.windowSeconds = windowSeconds;
            this.windowStartMs = System.currentTimeMillis();
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long elapsed = now - windowStartMs;

            // 窗口滑动：超过时间窗口则重置
            if (elapsed >= windowSeconds * 1000L) {
                synchronized (this) {
                    // 双重检查
                    if (System.currentTimeMillis() - windowStartMs >= windowSeconds * 1000L) {
                        windowStartMs = System.currentTimeMillis();
                        counter.set(0);
                    }
                }
            }

            int current = counter.incrementAndGet();
            if (current <= maxCalls) {
                return true;
            }
            return false;
        }
    }
}