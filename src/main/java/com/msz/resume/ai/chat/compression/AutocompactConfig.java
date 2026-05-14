package com.msz.resume.ai.chat.compression;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * L5 Autocompact 配置类
 *
 * <p>控制自动压缩的触发阈值、尾部保护和压缩后恢复行为。
 *
 * <p>配置示例（application.yml）：
 * <pre>
 * jarvis:
 *   autocompact:
 *     context-window: 128000
 *     reserved-output-tokens: 20000
 *     threshold-offset: 13000
 *     min-tokens-to-preserve: 10000
 *     min-messages-to-keep: 5
 *     max-tokens-to-preserve: 40000
 *     skill-restore-budget: 25000
 *     max-tokens-per-skill: 5000
 *     max-skills-to-restore: 5
 *     plan-restore-budget: 10000
 *     max-consecutive-failures: 3
 *     max-ptl-retries: 3
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jarvis.autocompact")
public class AutocompactConfig {

    // ==================== 触发阈值 ====================

    /** 模型上下文窗口大小（tokens） */
    private int contextWindow = 128_000;

    /** 预留输出 tokens */
    private int reservedOutputTokens = 20_000;

    /** 触发阈值偏移量 */
    private int thresholdOffset = 13_000;

    // ==================== 尾部保护 ====================

    /** 最少保留的 tokens */
    private int minTokensToPreserve = 10_000;

    /** 最少保留的消息条数 */
    private int minMessagesToKeep = 5;

    /** 最多保留的 tokens（硬上限） */
    private int maxTokensToPreserve = 40_000;

    // ==================== 压缩后恢复 ====================

    /** Skill 恢复预算（tokens） */
    private int skillRestoreBudget = 25_000;

    /** 单个 Skill 最大 tokens */
    private int maxTokensPerSkill = 5_000;

    /** 最多恢复 Skill 数量 */
    private int maxSkillsToRestore = 5;

    /** Plan 恢复预算（tokens） */
    private int planRestoreBudget = 10_000;

    // ==================== 熔断器 ====================

    /** 最大连续失败次数（熔断器阈值） */
    private int maxConsecutiveFailures = 3;

    /** PTL (Prompt Too Long) 重试次数 */
    private int maxPtlRetries = 3;

    // ==================== 计算方法 ====================

    /**
     * 计算触发压缩的阈值
     *
     * @return 触发压缩的最小 token 数
     */
    public int getTriggerThreshold() {
        return contextWindow - reservedOutputTokens - thresholdOffset;
    }
}
