package com.msz.resume.ai.chat.compression;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上下文压缩配置类
 *
 * <p>控制工具结果预算裁剪、微压缩和预处理管线的行为。
 *
 * <p>配置示例（application-dev.yml）：
 * <pre>
 * jarvis:
 *   compression:
 *     max-result-size-chars: 50000
 *     preview-size-bytes: 2000
 *     max-tool-results-per-message-chars: 200000
 *     keep-recent: 5
 *     context-threshold-ratio: 0.85
 *     model-context-window: 32768
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "jarvis.compression")
public class JarvisCompressionProperties {

    /** 单个工具结果最大字符数（超过则持久化到数据库） */
    private int maxResultSizeChars = 50000;

    /** 预览大小（字节） */
    private int previewSizeBytes = 2000;

    /** 单轮所有工具结果总字符上限 */
    private int maxToolResultsPerMessageChars = 200000;

    /** L3保留最近N个工具结果 */
    private int keepRecent = 5;

    /** 上下文利用率阈值（超过则触发压缩） */
    private double contextThresholdRatio = 0.85;

    /** 模型上下文窗口大小 */
    private int modelContextWindow = 32768;

    // ==================== Getters & Setters ====================

    public int getMaxResultSizeChars() {
        return maxResultSizeChars;
    }

    public void setMaxResultSizeChars(int maxResultSizeChars) {
        this.maxResultSizeChars = maxResultSizeChars;
    }

    public int getPreviewSizeBytes() {
        return previewSizeBytes;
    }

    public void setPreviewSizeBytes(int previewSizeBytes) {
        this.previewSizeBytes = previewSizeBytes;
    }

    public int getMaxToolResultsPerMessageChars() {
        return maxToolResultsPerMessageChars;
    }

    public void setMaxToolResultsPerMessageChars(int maxToolResultsPerMessageChars) {
        this.maxToolResultsPerMessageChars = maxToolResultsPerMessageChars;
    }

    public int getKeepRecent() {
        return keepRecent;
    }

    public void setKeepRecent(int keepRecent) {
        this.keepRecent = keepRecent;
    }

    public double getContextThresholdRatio() {
        return contextThresholdRatio;
    }

    public void setContextThresholdRatio(double contextThresholdRatio) {
        this.contextThresholdRatio = contextThresholdRatio;
    }

    public int getModelContextWindow() {
        return modelContextWindow;
    }

    public void setModelContextWindow(int modelContextWindow) {
        this.modelContextWindow = modelContextWindow;
    }
}
