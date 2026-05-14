/**
 * SessionPersistenceService 接口：会话持久化服务
 *
 * 采用"场景驱动"设计，只暴露 4 个方法，每个对应一个业务场景：
 * - completeRound: 一轮对话完成后保存
 * - restoreSession: 恢复会话
 * - closeSession: 软删除会话
 * - listActiveSessions: 列出活跃会话
 */
package com.msz.resume.ai.chat.session.service;

import com.msz.resume.ai.chat.session.model.SessionSnapshot;
import com.msz.resume.ai.chat.session.model.SessionSummary;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Map;


public interface SessionPersistenceService {

    /**
     * 一轮对话完成后保存
     * 内部处理：新建/更新会话 + 批量写入消息 + 更新Token和时间
     *
     * @param sessionId 会话ID
     * @param state 会话状态（包含 Token 用量等）
     * @param messages 本轮产生的消息列表
     */
    void completeRound(String sessionId, String ownerUsername, SessionState state, List<ChatMessage> messages);

    /**
     * 一轮流式对话完成后保存，并把用户可见的 action timeline 写入独立 UI timeline store。
     * 这些 action records 只用于历史重放，不参与后续 LLM prompt 恢复。
     */
    void completeRound(String sessionId,
                       String ownerUsername,
                       SessionState state,
                       List<ChatMessage> messages,
                       List<Map<String, Object>> timelineActions);

    /**
     * 只保存用户可见的 UI timeline actions，不保存 LLM message history。
     * 用于 AskUserQuestion pending 等不能持久化未配对工具调用消息的场景。
     */
    void persistTimelineActions(String sessionId,
                                String ownerUsername,
                                SessionState state,
                                int anchorMessageIndex,
                                List<Map<String, Object>> timelineActions);

    /**
     * 恢复会话（含消息历史）
     *
     * @param sessionId 会话ID
     * @return SessionSnapshot（包含会话状态和消息历史），如果会话不存在或已关闭返回 null
     */
    SessionSnapshot restoreSession(String sessionId, String ownerUsername);

    /**
     * 软删除会话（status=closed）
     * 关闭后的会话无法继续对话
     *
     * @param sessionId 会话ID
     */
    void closeSession(String sessionId, String ownerUsername);

    /** 重命名会话标题 */
    SessionSummary renameSession(String sessionId, String ownerUsername, String title);

    /** 置顶或取消置顶会话 */
    SessionSummary pinSession(String sessionId, String ownerUsername, boolean pinned);

    /**
     * 列出所有活跃会话
     *
     * @return 活跃会话的状态列表
     */
    List<SessionSummary> listActiveSessions(String ownerUsername);
}
