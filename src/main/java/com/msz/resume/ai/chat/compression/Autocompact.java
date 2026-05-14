package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.AutocompactResult;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * L5 自动压缩器接口
 *
 * <p>作为最终兜底手段，调用 LLM 将全部对话历史压缩为一份精简摘要。
 *
 * <p>触发条件：
 * <ul>
 *   <li>上下文利用率 > 89.5%</li>
 *   <li>L4 未启用或未活跃</li>
 * </ul>
 *
 * <p>压缩策略：
 * <ul>
 *   <li>使用专门的压缩 Prompt 引导 LLM 生成结构化摘要</li>
 *   <li>摘要包含 9 个标准 section（请求意图、技术概念、代码文件等）</li>
 *   <li>压缩请求禁止调用工具</li>
 *   <li>熔断器：连续失败 3 次后停止尝试</li>
 * </ul>
 */
public interface Autocompact {

    /**
     * 执行全量摘要压缩
     *
     * <p>将整个对话历史压缩为一份精简摘要。
     *
     * @param messages 原始消息列表
     * @param sessionId 会话ID
     * @return 压缩结果
     */
    AutocompactResult compact(List<ChatMessage> messages, String sessionId);

    /**
     * 判断是否需要压缩
     *
     * @param tokens 当前 Token 数
     * @return 是否超过触发阈值
     */
    boolean needsCompact(int tokens);

    /**
     * 检查熔断器是否已触发
     *
     * @param sessionId 会话ID
     * @return 是否应停止尝试压缩
     */
    boolean isCircuitBreakerTripped(String sessionId);

    /**
     * 重置熔断器（新会话开始时调用）
     *
     * @param sessionId 会话ID
     */
    void resetCircuitBreaker(String sessionId);

    /**
     * 获取连续失败次数
     *
     * @param sessionId 会话ID
     * @return 连续失败次数
     */
    int getConsecutiveFailures(String sessionId);
}
