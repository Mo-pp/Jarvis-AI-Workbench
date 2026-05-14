package com.msz.resume.ai.chat.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 对话响应DTO
 * 返回给前端的对话结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    /**
     * 会话ID
     * 前端需要保存此ID，用于后续对话或会话恢复
     */
    private String sessionId;

    /**
     * AI回复内容
     * 大模型生成的文本回复
     */
    private String aiMessage;

    private String mindmapData;

    private String questionnaireData;

    /**
     * 前端工作台可识别的统一产物列表。
     *
     * <p>mindmap/questionnaire/resume/optimize_result 等都通过该字段返回，
     * 前端只根据 type 选择组件，不再从普通聊天正文里猜测 JSON 类型。
     */
    private List<ChatArtifact> artifacts;

    /**
     * 完整的对话历史（可选）
     * 包含用户输入、AI回复、工具调用结果等
     */
    private List<Object> messageHistory;

    /**
     * Token用量统计
     */
    private TokenUsage tokenUsage;

    /**
     * 执行状态
     * success/failure/timeout/pending
     */
    private String status;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    // ==================== AskUserQuestion 阻塞式支持 ====================

    /**
     * 挂起会话ID
     * 当 LLM 调用 AskUserQuestion 工具时设置
     * 前端需要保存此ID，用于 POST /api/chat/answer 恢复会话
     */
    private String pendingId;

    /**
     * 待回答的问题列表
     * 当 LLM 调用 AskUserQuestion 工具时设置
     * 前端根据此字段渲染问答 UI
     */
    private List<?> pendingQuestions;

    /**
     * 是否需要用户输入
     * 为 true 时，前端应渲染问答 UI 并等待用户回答
     */
    @Builder.Default
    private Boolean requiresUserInput = false;

    // ==================== 任务规划进度支持 ====================

    /**
     * 当前任务计划
     * LLM 通过 TaskPlanTool 创建的任务列表，前端可用于渲染进度面板
     */
    private List<Map<String, Object>> taskPlan;

    /**
     * 任务进度统计
     * 包含 total/inProgress/completed/pending/skipped 各状态数量
     */
    private Map<String, Integer> taskProgress;

    /**
     * Token用量内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;
    }
}
