package com.msz.resume.ai.chat.compression;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Token 估算器接口
 *
 * <p>估算消息列表的 Token 数量，使用 API 返回值作为锚点提高精度。
 *
 * <p>锚点法原理：
 * <ol>
 *   <li>每次 API 返回后，用服务端报告的 input_tokens 作为锚点</li>
 *   <li>估算时，计算锚点之后新增消息的 Token 数</li>
 *   <li>总 Token 数 = 锚点 + 增量估算</li>
 * </ol>
 *
 * <p>准确度：锚点法 ~5% 误差，纯字符估算 ~30%+ 误差。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 每次 API 返回后更新锚点
 * tokenEstimator.updateAnchor(response.tokenUsage().inputTokenCount());
 *
 * // 估算当前消息列表
 * int tokens = tokenEstimator.estimate(messages);
 * }</pre>
 */
public interface TokenEstimator {

    /**
     * 估算消息列表的 Token 数
     *
     * <p>算法：
     * <ul>
     *   <li>有锚点：锚点 + 增量消息的字符数 × 系数</li>
     *   <li>无锚点：全量消息的字符数 × 系数</li>
     * </ul>
     *
     * @param messages 消息列表
     * @return 估算的 Token 数
     */
    int estimate(List<ChatMessage> messages);

    /**
     * 更新锚点（每次 API 返回后调用）
     *
     * <p>锚点来自 ChatResponse.tokenUsage().inputTokenCount()
     *
     * @param promptTokens API 返回的实际输入 Token 数
     */
    void updateAnchor(int promptTokens);

    /**
     * 更新锚点，并记录对应的消息数量
     *
     * <p>用于增量估算时判断哪些消息是新增的。
     *
     * @param promptTokens API 返回的实际输入 Token 数
     * @param messageCount 锚点对应的消息数量
     */
    void updateAnchor(int promptTokens, int messageCount);

    /**
     * 获取当前锚点值
     *
     * @return 上次更新的锚点，未更新则返回 0
     */
    int getAnchor();

    /**
     * 获取锚点对应的消息数量
     *
     * @return 锚点对应的消息数量，未更新则返回 0
     */
    int getAnchorMessageCount();

    /**
     * 重置锚点（新会话开始时调用）
     */
    void reset();
}
