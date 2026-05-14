package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.CompactResult;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Set;

/**
 * 微压缩器接口
 *
 * <p>清理过时的可压缩工具输出，释放上下文空间。
 *
 * <p>可压缩工具类型（COMPACTABLE_TOOLS）：
 * <ul>
 *   <li>fileRead - 文件读取</li>
 *   <li>shell - Shell 命令</li>
 *   <li>grep - 内容搜索</li>
 *   <li>glob - 文件搜索</li>
 *   <li>webSearch - Web 搜索</li>
 *   <li>webFetch - Web 抓取</li>
 *   <li>fileEdit - 文件编辑</li>
 *   <li>fileWrite - 文件写入</li>
 * </ul>
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
 */
public interface Microcompact {

    /** 清除标记，替换被清理的工具结果内容 */
    String CLEARED_MESSAGE = "[旧工具结果已清理]";

    /**
     * 压缩消息列表中的旧工具输出
     *
     * <p>规则：
     * <ul>
     *   <li>扫描 COMPACTABLE_TOOLS 的 tool_result</li>
     *   <li>保留最近 keepRecent 个的完整内容</li>
     *   <li>更早的替换为 [旧工具结果已清理]</li>
     *   <li>消息数量、顺序、结构不变</li>
     * </ul>
     *
     * @param messages 原始消息列表
     * @return 压缩结果，包含新消息列表和统计信息
     */
    CompactResult compact(List<ChatMessage> messages);

    /**
     * 获取可压缩工具名称集合
     *
     * @return 不可修改的工具名称集合
     */
    Set<String> getCompactableTools();

    /**
     * 判断工具是否可压缩
     *
     * @param toolName 工具名称
     * @return 是否在可压缩工具列表中
     */
    boolean isCompactable(String toolName);
}
