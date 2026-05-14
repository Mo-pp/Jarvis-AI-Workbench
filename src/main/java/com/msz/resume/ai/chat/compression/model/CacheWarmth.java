package com.msz.resume.ai.chat.compression.model;

/**
 * 缓存热度枚举
 *
 * <p>用于描述LLM API前缀缓存的命中状态，帮助监控缓存效率。
 *
 * <p>热度判定规则：
 * <ul>
 *   <li>COLD: 命中率 &lt; 30% — 缓存几乎未命中，可能需要检查提示词稳定性</li>
 *   <li>WARM: 命中率 30% ~ 70% — 缓存部分命中，正常状态</li>
 *   <li>HOT: 命中率 &gt; 70% — 缓存高效命中，理想状态</li>
 * </ul>
 *
 * <p>注意：此枚举仅用于日志监控，不直接控制压缩决策。
 */
public enum CacheWarmth {

    /** 冷缓存：命中率 < 30% */
    COLD("冷", "命中率低于30%，缓存效率低"),

    /** 温缓存：命中率 30% ~ 70% */
    WARM("温", "命中率30%~70%，缓存效率一般"),

    /** 热缓存：命中率 > 70% */
    HOT("热", "命中率高于70%，缓存效率高");

    private final String label;
    private final String description;

    CacheWarmth(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据命中率判定缓存热度
     *
     * @param hitRate 命中率（0.0 ~ 1.0）
     * @return 对应的缓存热度
     */
    public static CacheWarmth fromHitRate(double hitRate) {
        if (hitRate < 0.3) {
            return COLD;
        } else if (hitRate <= 0.7) {
            return WARM;
        } else {
            return HOT;
        }
    }
}
