package com.msz.resume.ai.chat.compression.model;

/**
 * 预算裁剪结果记录
 *
 * <p>描述工具执行结果经过L1预算裁剪后的处理结果。
 *
 * <p>使用示例：
 * <pre>{@code
 * BudgetResult result = toolResultBudget.process("fileRead", largeContent, sessionId);
 * if (result.truncated()) {
 *     log.info("结果已持久化到: {}", result.persistedPath());
 * }
 * return result.content();
 * }</pre>
 *
 * @param content       处理后的内容（原样或预览）
 * @param truncated     是否被截断
 * @param persistedPath 持久化路径（null表示未持久化）
 * @param originalSize  原始大小（字符数）
 * @param resultSize    结果大小（字符数）
 */
public record BudgetResult(
    String content,
    boolean truncated,
    String persistedPath,
    int originalSize,
    int resultSize
) {

    /**
     * 创建未改变的结果（内容原样保留）
     *
     * @param content 原始内容
     * @return BudgetResult，truncated=false
     */
    public static BudgetResult unchanged(String content) {
        if (content == null) {
            return new BudgetResult("", false, null, 0, 0);
        }
        return new BudgetResult(content, false, null, content.length(), content.length());
    }

    /**
     * 创建截断的结果
     *
     * @param previewContent 预览内容
     * @param persistedPath  持久化路径
     * @param originalSize   原始大小
     * @return BudgetResult，truncated=true
     */
    public static BudgetResult truncated(String previewContent, String persistedPath, int originalSize) {
        return new BudgetResult(previewContent, true, persistedPath, originalSize, previewContent.length());
    }

    /**
     * 计算节省的字符数
     *
     * @return 原始大小与结果大小的差值
     */
    public int charsSaved() {
        return originalSize - resultSize;
    }
}
