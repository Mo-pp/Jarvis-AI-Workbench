package com.msz.resume.ai.integrations.openviking.core.session;

import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;

import java.util.Optional;

/**
 * OpenViking Session 操作网关。
 *
 * <p>作为 JARVIS 主对话链路与 OpenViking Session API 之间的集成边界。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *     <li>最佳努力原则：失败不阻断主对话，返回 false 或 empty</li>
 *     <li>幂等性：ensureSession 支持重复调用，已存在视为成功</li>
 *     <li>统一日志：所有操作记录 WARN 级别日志，不暴露敏感信息</li>
 * </ul>
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>负责 OpenViking Session 生命周期管理（create/append/context/commit）</li>
 *     <li>负责消息格式转换（JARVIS → OpenViking Simple Message）</li>
 *     <li>负责错误降级和日志记录</li>
 * </ul>
 *
 * <p>不负责：</p>
 * <ul>
 *     <li>不负责 MySQL 会话持久化（由 SessionPersistenceService 负责）</li>
 *     <li>不负责长期记忆管理（由 OpenVikingUserMemoryService 负责）</li>
 *     <li>不负责 LLM 工具调用（由 OpenVikingSearchTool 负责）</li>
 * </ul>
 */
public interface OpenVikingSessionGateway {

    /**
     * 确保 OpenViking Session 存在。
     *
     * <p>幂等操作：如果 session 已存在，视为成功；如果不存在，尝试创建。</p>
     *
     * @param sessionId JARVIS 会话 ID，直接作为 OpenViking session_id
     * @return true 表示 session 可用（已存在或创建成功），false 表示失败
     */
    boolean ensureSession(String sessionId);

    default boolean ensureSession(String sessionId, OpenVikingIdentity identity) {
        return ensureSession(sessionId);
    }

    /**
     * 追加用户消息到 OpenViking Session。
     *
     * @param sessionId JARVIS 会话 ID
     * @param content 用户消息内容
     * @return true 表示追加成功，false 表示失败
     */
    boolean appendUserMessage(String sessionId, String content);

    default boolean appendUserMessage(String sessionId, String content, OpenVikingIdentity identity) {
        return appendUserMessage(sessionId, content);
    }

    /**
     * 追加助手消息到 OpenViking Session。
     *
     * @param sessionId JARVIS 会话 ID
     * @param content 助手消息内容
     * @return true 表示追加成功，false 表示失败
     */
    boolean appendAssistantMessage(String sessionId, String content);

    default boolean appendAssistantMessage(String sessionId, String content, OpenVikingIdentity identity) {
        return appendAssistantMessage(sessionId, content);
    }

    /**
     * 加载 OpenViking Session Context。
     *
     * <p>用于压缩/恢复场景，获取当前会话的结构化摘要上下文。</p>
     *
     * @param sessionId JARVIS 会话 ID
     * @param tokenBudget Token 预算限制，null 表示使用服务端默认值
     * @return 格式化后的 Session Context Markdown section，失败或为空时返回 empty
     */
    Optional<String> loadSessionContext(String sessionId, Integer tokenBudget);

    default Optional<String> loadSessionContext(String sessionId, Integer tokenBudget, OpenVikingIdentity identity) {
        return loadSessionContext(sessionId, tokenBudget);
    }

    /**
     * 提交 OpenViking Session 进行归档。
     *
     * <p>触发后台任务生成 archive 并提取长期记忆。</p>
     *
     * @param sessionId JARVIS 会话 ID
     * @return task_id 用于追踪后台任务状态，失败时返回 empty
     */
    Optional<String> commitSession(String sessionId);

    default Optional<String> commitSession(String sessionId, OpenVikingIdentity identity) {
        return commitSession(sessionId);
    }
}
