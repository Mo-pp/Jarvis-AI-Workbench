package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.BudgetResult;

/**
 * 工具结果预算裁剪器接口
 *
 * <p>对工具执行结果进行大小控制，超限结果持久化到数据库并返回预览。
 *
 * <p>使用示例：
 * <pre>{@code
 * BudgetResult result = toolResultBudget.process("fileRead", toolCallId, largeContent, sessionId);
 * if (result.truncated()) {
 *     log.info("结果已持久化，ID: {}", result.persistedPath());
 * }
 * return result.content();
 * }</pre>
 */
public interface ToolResultBudget {

    /**
     * 对工具结果应用预算裁剪
     *
     * <p>规则：
     * <ul>
     *   <li>结果 ≤ maxResultSizeChars: 原样返回</li>
     *   <li>结果 > maxResultSizeChars: 持久化到数据库，返回 previewSizeBytes 预览</li>
     * </ul>
     *
     * @param toolName 工具名称（用于日志和记录）
     * @param toolCallId 工具调用ID（用于后续检索完整内容）
     * @param result 工具执行结果
     * @param sessionId 会话ID（用于存储）
     * @return 预算处理结果
     */
    BudgetResult process(String toolName, String toolCallId, String result, String sessionId);

    /**
     * 检查是否需要预算裁剪
     *
     * @param resultSize 结果字符数
     * @return 是否超过阈值
     */
    boolean needsTruncation(int resultSize);
}
