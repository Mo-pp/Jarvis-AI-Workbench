package com.msz.resume.ai.chat.runtime.node.inner.strategy;

import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.TraceAgentDescriptor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.Collections;
import java.util.List;

/**
 * 工具执行上下文
 *
 * <p>封装工具执行所需的所有输入数据，作为策略执行的参数传递。
 *
 * @param state              当前状态机状态
 * @param requests           未被 Hook 阻断的请求列表
 * @param blockedResults     Hook 阻断的结果消息
 * @param blockedRequests    Hook 阻断的请求列表
 */
public record ToolExecutionContext(
    QueryLoopState state,
    OpenVikingIdentity openVikingIdentity,
    ChatRunTraceContext traceContext,
    TraceAgentDescriptor agentDescriptor,
    List<ToolExecutionRequest> requests,
    List<ToolExecutionResultMessage> blockedResults,
    List<ToolExecutionRequest> blockedRequests
) {

    /**
     * 获取主要的（第一个）工具请求
     *
     * <p>用于策略选择判断。当 LLM 并行调用多个工具时，
     * 通常以第一个工具的类型决定整体处理策略。
     *
     * @return 第一个请求，如果没有则返回 null
     */
    public ToolExecutionRequest primaryRequest() {
        return requests != null && !requests.isEmpty() ? requests.get(0) : null;
    }

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
        private QueryLoopState state;
        private OpenVikingIdentity openVikingIdentity;
        private ChatRunTraceContext traceContext;
        private TraceAgentDescriptor agentDescriptor;
        private List<ToolExecutionRequest> requests = Collections.emptyList();
        private List<ToolExecutionResultMessage> blockedResults = Collections.emptyList();
        private List<ToolExecutionRequest> blockedRequests = Collections.emptyList();

        public Builder state(QueryLoopState state) {
            this.state = state;
            return this;
        }

        public Builder openVikingIdentity(OpenVikingIdentity openVikingIdentity) {
            this.openVikingIdentity = openVikingIdentity;
            return this;
        }

        public Builder traceContext(ChatRunTraceContext traceContext) {
            this.traceContext = traceContext;
            return this;
        }

        public Builder agentDescriptor(TraceAgentDescriptor agentDescriptor) {
            this.agentDescriptor = agentDescriptor;
            return this;
        }

        public Builder requests(List<ToolExecutionRequest> requests) {
            this.requests = requests;
            return this;
        }

        public Builder blockedResults(List<ToolExecutionResultMessage> blockedResults) {
            this.blockedResults = blockedResults;
            return this;
        }

        public Builder blockedRequests(List<ToolExecutionRequest> blockedRequests) {
            this.blockedRequests = blockedRequests;
            return this;
        }

        public ToolExecutionContext build() {
            return new ToolExecutionContext(state, openVikingIdentity, traceContext, agentDescriptor, requests, blockedResults, blockedRequests);
        }
    }
}
