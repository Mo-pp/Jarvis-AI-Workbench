package com.msz.resume.ai.chat.compression.model;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 压缩结果记录
 *
 * <p>描述L3 Microcompact清理旧工具输出后的结果。
 *
 * <p>使用示例：
 * <pre>{@code
 * CompactResult result = microcompact.compact(messages);
 * if (result.compactedCount() > 0) {
 *     log.info("清理了 {} 个工具结果，节省 {} tokens",
 *         result.compactedCount(), result.tokensSaved());
 * }
 * messages = result.messages();
 * }</pre>
 *
 * @param messages            处理后的消息列表
 * @param compactedCount      清理的工具结果数量
 * @param tokensSaved         节省的Token估算值
 * @param compactedToolNames  被清理的工具名称列表
 */
public record CompactResult(
    List<ChatMessage> messages,
    int compactedCount,
    int tokensSaved,
    List<String> compactedToolNames
) {

    /**
     * 创建未改变的结果（消息原样保留）
     *
     * @param messages 原始消息列表
     * @return CompactResult，compactedCount=0
     */
    public static CompactResult unchanged(List<ChatMessage> messages) {
        return new CompactResult(messages, 0, 0, List.of());
    }

    /**
     * 判断是否执行了压缩
     *
     * @return compactedCount > 0
     */
    public boolean wasCompacted() {
        return compactedCount > 0;
    }
}
