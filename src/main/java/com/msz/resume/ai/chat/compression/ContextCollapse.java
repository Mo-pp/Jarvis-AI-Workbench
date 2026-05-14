package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.CollapseResult;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * L4 上下文折叠器接口（投影式折叠）
 *
 * <p>核心设计：折叠操作不修改原始消息，只记录折叠状态，发送前生成投影视图。
 *
 * <p>架构流程：
 * <pre>
 * 原始消息（状态中，始终不变）：
 *   [Msg0] [Msg1] [Msg2] [Msg3] [Msg4] [Msg5] [Msg6]
 *                 ↑                           ↑
 *            startIndex=2                 endIndex=6
 *
 * collapse() 只记录折叠状态到数据库：
 *   FoldedRecord(sessionId, groupId, 2, 6, 4, 1000)
 *
 * projectView() 生成投影视图（发送给 API）：
 *   [Msg0] [Msg1] [折叠摘要] [Msg6]
 *
 * releaseFolded() 释放折叠：
 *   删除 FoldedRecord → 下次 projectView() 返回原始消息
 * </pre>
 *
 * <p>触发条件：
 * <ul>
 *   <li>上下文利用率 > 90%: 启动折叠</li>
 *   <li>上下文利用率 > 95%: 阻塞工具调用</li>
 * </ul>
 *
 * <p>与 L5 的关系：
 * <ul>
 *   <li>L4 启用并活跃时，L5 被抑制</li>
 *   <li>PTL 恢复时优先释放 L4 的折叠</li>
 * </ul>
 */
public interface ContextCollapse {

    /** 折叠摘要消息前缀 */
    String FOLDED_SUMMARY_PREFIX = "[早期对话已折叠]";

    /**
     * 记录折叠状态（不修改消息）
     *
     * <p>只将折叠的元数据（消息索引范围）保存到数据库。
     * 原始消息列表保持不变。
     *
     * @param messageCount 当前消息总数
     * @param tokens       当前 Token 数
     * @param sessionId    会话ID
     * @return 折叠结果（包含折叠组ID等信息，但消息列表不变）
     */
    CollapseResult recordCollapse(int messageCount, int tokens, String sessionId);

    /**
     * 生成投影视图
     *
     * <p>根据数据库中的折叠记录，将原始消息列表转换为折叠视图。
     * 这是要发送给 API 的消息列表。
     *
     * @param messages  原始消息列表
     * @param sessionId 会话ID
     * @return 投影视图（折叠后的消息列表）
     */
    List<ChatMessage> projectView(List<ChatMessage> messages, String sessionId);

    /**
     * 释放最新的折叠组
     *
     * <p>当 API 返回 PTL（Prompt-Too-Long）错误时调用。
     * 删除最新的折叠记录，下次 projectView() 将包含更多原始消息。
     *
     * @param sessionId 会话ID
     * @return 释放的折叠组数量
     */
    int releaseFolded(String sessionId);

    /**
     * 判断是否需要折叠
     *
     * @param tokens 当前 Token 数
     * @return 是否超过启动阈值（90%）
     */
    boolean needsCollapse(int tokens);

    /**
     * 判断是否需要阻塞工具调用
     *
     * @param tokens 当前 Token 数
     * @return 是否超过阻塞阈值（95%）
     */
    boolean shouldBlockTools(int tokens);

    /**
     * 检查是否有活跃的折叠记录
     *
     * @param sessionId 会话ID
     * @return 是否有折叠记录
     */
    boolean isActive(String sessionId);

    /**
     * 获取折叠组数量
     *
     * @param sessionId 会话ID
     * @return 折叠组数量
     */
    int getFoldGroupCount(String sessionId);
}
