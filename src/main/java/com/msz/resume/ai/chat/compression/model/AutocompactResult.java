package com.msz.resume.ai.chat.compression.model;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 自动压缩结果记录
 *
 * <p>描述 L5 Autocompact 全量摘要压缩后的结果。
 *
 * @param messages         处理后的消息列表（摘要消息）
 * @param originalTokens   原始 Token 数
 * @param compactedTokens  压缩后 Token 数
 * @param success          是否成功
 * @param errorMessage     错误信息（失败时）
 */
public record AutocompactResult(
    List<ChatMessage> messages,
    int originalTokens,
    int compactedTokens,
    boolean success,
    String errorMessage
) {

    /**
     * 创建成功的结果
     *
     * @param messages        压缩后的消息列表
     * @param originalTokens  原始 Token 数
     * @param compactedTokens 压缩后 Token 数
     * @return 成功的 AutocompactResult
     */
    public static AutocompactResult success(List<ChatMessage> messages, int originalTokens, int compactedTokens) {
        return new AutocompactResult(messages, originalTokens, compactedTokens, true, null);
    }

    /**
     * 创建失败的结果
     *
     * @param originalTokens 原始 Token 数
     * @param errorMessage   错误信息
     * @return 失败的 AutocompactResult
     */
    public static AutocompactResult failure(int originalTokens, String errorMessage) {
        return new AutocompactResult(null, originalTokens, 0, false, errorMessage);
    }

    /**
     * 创建跳过的结果（不需要压缩）
     *
     * @param messages 原始消息列表
     * @param tokens   Token 数
     * @return 跳过的 AutocompactResult
     */
    public static AutocompactResult skipped(List<ChatMessage> messages, int tokens) {
        return new AutocompactResult(messages, tokens, tokens, true, null);
    }

    /**
     * 计算节省的 Token 数
     *
     * @return originalTokens - compactedTokens
     */
    public int tokensSaved() {
        return originalTokens - compactedTokens;
    }
}
