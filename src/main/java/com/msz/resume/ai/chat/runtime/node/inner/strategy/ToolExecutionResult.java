package com.msz.resume.ai.chat.runtime.node.inner.strategy;

import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具执行结果
 *
 * <p>封装工具执行后的所有输出数据，由 ExecuteToolNode 汇总后更新状态。
 *
 * @param messages         工具执行结果消息列表
 * @param contexts         工具调用上下文列表
 * @param transition       状态转移标记
 * @param discoveredTools  新发现的工具名称集合
 * @param taskPlan         任务计划列表
 * @param pendingId        挂起会话 ID（仅 AskUserQuestion 使用）
 * @param pendingQuestions 待回答的问题列表（仅 AskUserQuestion 使用）
 * @param pendingConfirmedRequest 确认后要执行的工具请求（仅删除确认 Hook 使用）
 */
public record ToolExecutionResult(
    List<ToolExecutionResultMessage> messages,
    List<ToolExecutionRequest> contexts,
    String transition,
    Set<String> discoveredTools,
    List<Map<String, Object>> taskPlan,
    String pendingId,
    List<QuestionDto> pendingQuestions,
    ToolExecutionRequest pendingConfirmedRequest
) {

    /**
     * 默认成功转移标记
     */
    public static final String TRANSITION_SUCCESS = "tool_executed_success";

    /**
     * 默认失败转移标记
     */
    public static final String TRANSITION_FAILED = "tool_executed_failed";

    /**
     * 挂起等待用户输入标记
     */
    public static final String TRANSITION_PENDING = "pending_user_input";

    /**
     * 工具已经产出前端可展示的 artifact，本轮可以直接结束。
     */
    public static final String TRANSITION_ARTIFACT_READY = "artifact_ready";

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器
     */
    public static class Builder {
        private List<ToolExecutionResultMessage> messages = new ArrayList<>();
        private List<ToolExecutionRequest> contexts = new ArrayList<>();
        private String transition = TRANSITION_SUCCESS;
        private Set<String> discoveredTools = Collections.emptySet();
        private List<Map<String, Object>> taskPlan;
        private String pendingId;
        private List<QuestionDto> pendingQuestions;
        private ToolExecutionRequest pendingConfirmedRequest;

        public Builder messages(List<ToolExecutionResultMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder addMessage(ToolExecutionResultMessage message) {
            this.messages.add(message);
            return this;
        }

        public Builder contexts(List<ToolExecutionRequest> contexts) {
            this.contexts = contexts;
            return this;
        }

        public Builder addContext(ToolExecutionRequest context) {
            this.contexts.add(context);
            return this;
        }

        public Builder transition(String transition) {
            this.transition = transition;
            return this;
        }

        public Builder discoveredTools(Set<String> discoveredTools) {
            this.discoveredTools = discoveredTools;
            return this;
        }

        public Builder taskPlan(List<Map<String, Object>> taskPlan) {
            this.taskPlan = taskPlan;
            return this;
        }

        public Builder pendingId(String pendingId) {
            this.pendingId = pendingId;
            return this;
        }

        public Builder pendingQuestions(List<QuestionDto> pendingQuestions) {
            this.pendingQuestions = pendingQuestions;
            return this;
        }

        public Builder pendingConfirmedRequest(ToolExecutionRequest pendingConfirmedRequest) {
            this.pendingConfirmedRequest = pendingConfirmedRequest;
            return this;
        }

        public ToolExecutionResult build() {
            return new ToolExecutionResult(
                messages,
                contexts,
                transition,
                discoveredTools,
                taskPlan,
                pendingId,
                pendingQuestions,
                pendingConfirmedRequest
            );
        }
    }
}
