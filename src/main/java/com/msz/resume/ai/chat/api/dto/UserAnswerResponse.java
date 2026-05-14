package com.msz.resume.ai.chat.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 用户答案响应 DTO
 *
 * <p>POST /api/chat/answer 的响应体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAnswerResponse {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 执行状态
     * success/failure/pending
     */
    private String status;

    /**
     * AI回复内容
     * 如果用户答案触发了新的 LLM 调用，这里是对话结果
     */
    private String aiMessage;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 如果用户答案又触发了新的 AskUserQuestion，这里是新的 pendingId
     */
    private String pendingId;

    /**
     * 新的待回答问题列表
     */
    private List<?> pendingQuestions;

    /**
     * Token用量统计
     */
    private ChatResponse.TokenUsage tokenUsage;

    // ==================== 任务规划进度支持 ====================

    /**
     * 当前任务计划
     */
    private List<Map<String, Object>> taskPlan;

    /**
     * 任务进度统计
     */
    private Map<String, Integer> taskProgress;
}
