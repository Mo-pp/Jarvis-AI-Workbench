package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.PipelineResult;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 消息预处理管线接口
 *
 * <p>串行执行压缩层级（L1 → L3 → L4 → L5），每层后检查上下文空间。
 *
 * <p>压缩层级说明：
 * <ul>
 *   <li>L1 Tool Result Budget - 控制单个工具结果大小</li>
 *   <li>L3 Microcompact - 清理过时的工具输出</li>
 *   <li>L4 Context Collapse - 投影式折叠（后续实现）</li>
 *   <li>L5 Autocompact - 全量摘要压缩（后续实现）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // CallLlmNode 中，构建 ChatRequest 之前
 * PipelineResult result = pipeline.process(messages, sessionId);
 * if (result.wasCompressed()) {
 *     log.info("执行压缩层级: {}, 节省 {} tokens",
 *         result.executedLevels(), result.originalTokens() - result.finalTokens());
 *     messages = result.messages();
 * }
 * }</pre>
 */
public interface MessagePreprocessingPipeline {

    /**
     * 执行完整的预处理管线
     *
     * <p>流程：
     * <ol>
     *   <li>估算当前 Token 数</li>
     *   <li>如果利用率 ≤ 阈值，跳过所有层级</li>
     *   <li>否则执行 L1（ToolResultBudget）</li>
     *   <li>检查空间，足够则跳过 L3</li>
     *   <li>执行 L3 (Microcompact)</li>
     *   <li>检查空间，足够则结束</li>
     *   <li>L4/L5 后续实现</li>
     * </ol>
     *
     * @param messages 原始消息列表
     * @param sessionId 会话ID（用于持久化）
     * @return 管线处理结果
     */
    PipelineResult process(List<ChatMessage> messages, String sessionId);

    /**
     * 计算上下文利用率
     *
     * @param tokens 当前 Token 数
     * @return 利用率 (0.0 ~ 1.0)
     */
    double calculateUtilization(int tokens);

    /**
     * 判断是否需要压缩
     *
     * @param tokens 当前 Token 数
     * @return 是否超过阈值
     */
    boolean needsCompression(int tokens);

    /**
     * 获取上下文窗口大小
     *
     * @return 模型上下文窗口大小（Token 数）
     */
    int getContextWindow();
}
