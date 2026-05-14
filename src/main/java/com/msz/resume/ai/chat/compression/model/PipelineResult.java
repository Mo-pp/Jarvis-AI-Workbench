package com.msz.resume.ai.chat.compression.model;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 管线处理结果记录
 *
 * <p>描述消息预处理管线（L1→L3）执行后的结果。
 *
 * <p>使用示例：
 * <pre>{@code
 * PipelineResult result = pipeline.process(messages, sessionId);
 * if (result.wasCompressed()) {
 *     log.info("执行压缩层级: {}, 节省 {} tokens",
 *         result.executedLevels(), result.originalTokens() - result.finalTokens());
 *     messages = result.messages();
 * }
 * }</pre>
 *
 * @param messages        处理后的消息列表
 * @param wasCompressed   是否执行了压缩
 * @param originalTokens  原始Token数
 * @param finalTokens     最终Token数
 * @param executedLevels  执行的压缩层级列表（如 ["L3"]）
 */
public record PipelineResult(
    List<ChatMessage> messages,
    boolean wasCompressed,
    int originalTokens,
    int finalTokens,
    List<String> executedLevels
) {

    /**
     * 创建未改变的结果（消息原样保留）
     *
     * @param messages 原始消息列表
     * @param tokens   当前Token数
     * @return PipelineResult，wasCompressed=false
     */
    public static PipelineResult unchanged(List<ChatMessage> messages, int tokens) {
        return new PipelineResult(messages, false, tokens, tokens, List.of());
    }

    /**
     * 计算节省的Token数
     *
     * @return originalTokens - finalTokens
     */
    public int tokensSaved() {
        return originalTokens - finalTokens;
    }
}
