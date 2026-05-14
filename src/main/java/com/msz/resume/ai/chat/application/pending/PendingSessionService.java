package com.msz.resume.ai.chat.application.pending;

import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.tooling.dto.PendingSession;
import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * 挂起会话服务接口
 *
 * <p>提供挂起会话的存储和恢复功能，用于 AskUserQuestion 工具的阻塞式实现。
 *
 * <h2>使用场景</h2>
 * <ol>
 *   <li>LLM 调用 AskUserQuestion 工具</li>
 *   <li>ExecuteToolNode 保存当前会话状态到 Redis</li>
 *   <li>用户回答后，AnswerController 从 Redis 恢复状态</li>
 *   <li>继续执行状态机</li>
 * </ol>
 *
 * <h2>Redis 存储结构</h2>
 * <pre>
 * Key: pending:{pendingId}
 * Value: JSON 序列化的 PendingSession
 * TTL: 30分钟
 * </pre>
 */
public interface PendingSessionService {

    /**
     * 保存挂起会话
     *
     * @param pendingId 挂起会话ID
     * @param sessionId 原始会话ID
     * @param state 当前 QueryLoopState
     * @param toolCallId 工具调用ID
     * @param questions 待回答的问题列表
     */
    void save(String pendingId, String sessionId, QueryLoopState state,
              String toolCallId, List<QuestionDto> questions);

    /**
     * 获取挂起会话
     *
     * @param pendingId 挂起会话ID
     * @return 挂起会话信息，不存在或已过期返回 null
     */
    PendingSession get(String pendingId);

    /**
     * 删除挂起会话
     *
     * @param pendingId 挂起会话ID
     */
    void delete(String pendingId);

    /**
     * 检查挂起会话是否存在
     *
     * @param pendingId 挂起会话ID
     * @return 是否存在
     */
    boolean exists(String pendingId);

    /**
     * 保存挂起会话，并在恢复前替换消息历史中的最后一次工具调用请求。
     * 用于危险操作确认：用户确认后继续执行已注入确认 token 的原工具请求。
     */
    void saveWithReplacementRequest(String pendingId, String sessionId, QueryLoopState state,
                                    String toolCallId, List<QuestionDto> questions,
                                    dev.langchain4j.agent.tool.ToolExecutionRequest replacementRequest,
                                    String confirmationToken);

    /**
     * 将挂起状态恢复为消息历史。
     */
    List<ChatMessage> deserializeMessages(PendingSession pendingSession);

    /**
     * 恢复状态时使用的工具名。
     */
    String resolveToolResultName(PendingSession pendingSession);

    /**
     * 根据挂起会话和用户答案生成恢复用工具结果文本。
     */
    String formatAnswersForLLM(PendingSession pendingSession, java.util.List<com.msz.resume.ai.chat.tooling.dto.UserAnswerDto> answers);

    /**
     * 反序列化消息历史
     *
     * @param stateJson 序列化的状态 JSON
     * @return 消息列表
     */
    List<ChatMessage> deserializeMessages(String stateJson);

    /**
     * 反序列化任务计划
     *
     * @param stateJson 序列化的状态 JSON
     * @return 任务计划列表，无任务时返回空列表
     */
    List<Map<String, Object>> deserializeTaskPlan(String stateJson);

    /**
     * 反序列化已自动召回并注入过的 OpenViking URI 层级。
     *
     * @param stateJson 序列化的状态 JSON
     * @return URI -> abstract/overview，无记录时返回空 Map
     */
    Map<String, String> deserializeSurfacedOpenVikingUris(String stateJson);

    /**
     * 从 JSON 反序列化 UserProfile
     *
     * @param userContextJson 用户上下文 JSON 字符串
     * @return UserProfile，解析失败返回 UserProfile.empty()
     */
    UserProfile deserializeUserProfile(String userContextJson);
}
