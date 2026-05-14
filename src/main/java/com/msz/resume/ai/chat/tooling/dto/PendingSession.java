package com.msz.resume.ai.chat.tooling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 挂起会话 DTO
 *
 * <p>当 AskUserQuestion 工具被调用时，会话状态被挂起并保存到 Redis。
 * 此 DTO 用于存储挂起会话的所有必要信息，以便用户回答后恢复执行。
 *
 * <h2>存储结构</h2>
 * <ul>
 *   <li><b>pendingId</b>: 挂起会话的唯一标识，用于恢复时查找</li>
 *   <li><b>sessionId</b>: 原始会话ID</li>
 *   <li><b>queryLoopStateJson</b>: 序列化的 QueryLoopState，包含消息历史等</li>
 *   <li><b>toolCallId</b>: 工具调用ID，用于构造 ToolExecutionResultMessage</li>
 *   <li><b>questions</b>: 待回答的问题列表</li>
 *   <li><b>createdAt</b>: 创建时间，用于计算过期</li>
 * </ul>
 *
 * <h2>Redis 存储</h2>
 * <pre>
 * Key: pending:{pendingId}
 * Value: JSON 序列化的 PendingSession
 * TTL: 30分钟
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingSession {

    /**
     * 挂起会话的唯一标识
     * UUID 格式，用于恢复时查找
     */
    private String pendingId;

    /**
     * 原始会话ID
     */
    private String sessionId;

    /**
     * 序列化的 QueryLoopState
     * 包含消息历史、工具上下文等状态信息
     */
    private String queryLoopStateJson;

    /**
     * 工具调用ID
     * 用于构造 ToolExecutionResultMessage
     */
    private String toolCallId;

    /**
     * 工具名称
     * 用于恢复时构造与原工具调用匹配的 ToolExecutionResultMessage
     */
    private String toolName;

    /**
     * 待回答的问题列表
     */
    private List<QuestionDto> questions;

    /**
     * 创建时间
     * 用于计算过期和调试
     */
    private LocalDateTime createdAt;

    /**
     * 会话状态数据的 Base64 编码
     * 用于跨语言兼容
     */
    private String stateDataBase64;

    /**
     * 用户上下文 JSON
     * 用于恢复时重建系统提示词
     */
    private String userContextJson;

    /**
     * OpenViking 租户身份。新挂起会话显式保存；旧数据可从 userContextJson 回退解析。
     */
    private OpenVikingIdentity openVikingIdentity;

    /**
     * 恢复前替换最后一条 AI 消息中的工具调用请求。
     * 用于删除确认这类危险操作，避免确认后再次触发同一个确认 Hook。
     */
    private String replacementToolRequestJson;

    /**
     * Hook 生成的确认 token。
     */
    private String confirmationToken;
}
