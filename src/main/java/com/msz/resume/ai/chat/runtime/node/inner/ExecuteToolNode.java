package com.msz.resume.ai.chat.runtime.node.inner;

import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamContext;
import com.msz.resume.ai.chat.runtime.trace.ToolActionEventService;
import com.msz.resume.ai.chat.runtime.trace.TraceAgentDescriptor;
import com.msz.resume.ai.chat.runtime.trace.TraceService;
import com.msz.resume.ai.hook.HookContext;
import com.msz.resume.ai.hook.HookEngine;
import com.msz.resume.ai.hook.HookResult;
import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentitySupport;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.node.inner.strategy.ToolExecutionContext;
import com.msz.resume.ai.chat.runtime.node.inner.strategy.ToolExecutionResult;
import com.msz.resume.ai.chat.runtime.node.inner.strategy.ToolExecutionStrategy;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.trace.langfuse.LangfuseTracingService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具执行协调节点。
 *
 * 作用：承接上一轮 LLM 产出的工具调用计划，先过 Hook，再选策略执行，
 * 最后把工具结果和 trace 元数据重新写回状态机。
 * 可以把它理解成“执行总控台”，LLM 开单，它负责分检、派工、收单。
 *
 * 代码逻辑：
 * 1. 读取最后一条 AiMessage 里的全部工具请求，并补齐当前 trace 上下文
 * 2. 先跑 Hook 预检，处理阻断
 * 3. 按请求类型选择普通工具策略或 spawnAgent 策略执行
 * 4. 根据结果更新 tool batch 的 trace 状态，并把消息、上下文信息写回 QueryLoopState
 *
 * @see ToolExecutionStrategy 工具执行策略接口
 * @see HookEngine Hook 拦截引擎
 */
@Slf4j
@Component
public class ExecuteToolNode implements AsyncNodeAction<QueryLoopState> {

    private final HookEngine hookEngine;
    private final List<ToolExecutionStrategy> strategies;
    private final TraceService traceService;
    private final ToolActionEventService toolActionEventService;
    private final LangfuseTracingService langfuseTracingService;

    /** 注入 Hook、执行策略和 trace 事件服务。 */
    public ExecuteToolNode(HookEngine hookEngine,
                           List<ToolExecutionStrategy> strategies,
                           TraceService traceService,
                           ToolActionEventService toolActionEventService,
                           LangfuseTracingService langfuseTracingService) {
        this.hookEngine = hookEngine;
        this.traceService = traceService;
        this.toolActionEventService = toolActionEventService;
        this.langfuseTracingService = langfuseTracingService;
        // 按优先级排序策略（数字越小优先级越高）
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(ToolExecutionStrategy::getPriority))
                .toList();
        log.info("[ExecuteToolNode] 已注册 {} 个工具执行策略", strategies.size());
    }
//====================================================================================
    @Override
    public CompletableFuture<Map<String, Object>> apply(QueryLoopState currentState) {

        OpenVikingIdentity identity = OpenVikingIdentitySupport.fromQueryLoopState(currentState);
        return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
            // 1. 拿到 LLM 发来的所有工具调用请求
            List<ChatMessage> messages = currentState.getMessages();
            AiMessage lastMessage = (AiMessage) messages.get(messages.size() - 1);
            List<ToolExecutionRequest> toolRequests = lastMessage.toolExecutionRequests();
            ChatRunTraceContext traceContext = ChatStreamContext.getTraceContext(
                    currentState.getSessionId(), currentState.getTraceRunId());
            TraceAgentDescriptor agentDescriptor = new TraceAgentDescriptor(
                    currentState.getTraceAgentId(),
                    currentState.getTraceAgentScope(),
                    currentState.getTraceAgentLabel(),
                    currentState.isSubAgent() ? currentState.getSubAgentType().name() : null
            );
            String batchStepId = traceContext != null
                    ? traceService.ensureToolBatch(traceContext, agentDescriptor, toolRequests)
                    : null;

            // 2. Hook 预检
            HookPreCheckResult preCheckResult = preCheckWithHooks(currentState, toolRequests, traceContext, agentDescriptor);

            // 如果所有工具都被 Hook 阻断，直接返回
            if (preCheckResult.activeRequests.isEmpty()) {
                ToolExecutionResult blockedResult = ToolExecutionResult.builder()
                        .messages(preCheckResult.blockedResults)
                        .contexts(preCheckResult.blockedRequests)
                        .transition("tool_executed_success")
                        .build();
                if (traceContext != null) {
                    traceService.completeToolBatch(traceContext, agentDescriptor, batchStepId);
                }
                return buildStateUpdate(blockedResult, currentState);
            }

            // 3. 选择策略并执行
            ToolExecutionContext context = ToolExecutionContext.builder()
                    .state(currentState)
                    .openVikingIdentity(identity)
                    .traceContext(traceContext)
                    .agentDescriptor(agentDescriptor)
                    .requests(preCheckResult.activeRequests)
                    .blockedResults(preCheckResult.blockedResults)
                    .blockedRequests(preCheckResult.blockedRequests)
                    .build();

            try {
                ToolExecutionStrategy strategy = selectStrategy(context);
                ToolExecutionResult result = strategy.execute(context);
                if (traceContext != null) {
                    if (ToolExecutionResult.TRANSITION_FAILED.equals(result.transition())) {
                        traceService.failToolBatch(traceContext, agentDescriptor, batchStepId);
                    } else {
                        traceService.completeToolBatch(traceContext, agentDescriptor, batchStepId);
                    }
                }

                // 4. 构建返回结果
                return buildStateUpdate(result, currentState);
            } catch (RuntimeException e) {
                if (traceContext != null) {
                    traceService.failToolBatch(traceContext, agentDescriptor, batchStepId);
                }
                throw e;
            }
        });
    }
//=================================================================================
    /**
     * Hook 预检：对所有工具请求执行 PreToolUse Hook
     *
     * <p>被 Hook 阻断的工具收集错误消息，不进入后续的策略执行。
     * 这确保 HookEngine 是唯一的拦截入口，替代硬编码的 if-else。
     */
    private HookPreCheckResult preCheckWithHooks(QueryLoopState state,
                                                 List<ToolExecutionRequest> requests,
                                                 ChatRunTraceContext traceContext,
                                                 TraceAgentDescriptor agentDescriptor) {
        List<ToolExecutionResultMessage> blockedResults = new ArrayList<>();
        List<ToolExecutionRequest> blockedRequests = new ArrayList<>();
        List<ToolExecutionRequest> activeRequests = new ArrayList<>();

        for (ToolExecutionRequest req : requests) {
            HookContext hookCtx = new HookContext(
                    req.name(), req.arguments(),
                    state, state.getSessionId(), req.id(), state.isSubAgent()
            );
            HookResult preResult = hookEngine.preToolUse(hookCtx);

            if (preResult.isBlocked()) {
                log.info("[ExecuteToolNode] PreToolUse Hook 阻断: tool={}, reason={}",
                        req.name(), preResult.blockReason());
                if (langfuseTracingService != null) {
                    langfuseTracingService.recordToolResult(traceContext, req, null, "blocked", preResult.blockReason());
                }
                if (traceContext != null) {
                    traceService.blockToolCall(traceContext, agentDescriptor, req);
                }
                toolActionEventService.toolBlocked(traceContext, agentDescriptor, req, preResult.blockReason());
                blockedResults.add(ToolExecutionResultMessage.from(req, preResult.blockReason()));
                blockedRequests.add(req);
            } else {
                activeRequests.add(req);
            }
        }

        return new HookPreCheckResult(activeRequests, blockedResults, blockedRequests);
    }

    /**
     * 选择策略：根据请求类型选择对应的执行策略
     *
     * <p>当一批工具调用里混有特殊工具（如 askUserQuestion、spawnAgent）时，
     * 不能只看第一个请求；否则普通工具排在前面会让特殊工具走默认执行路径。
     * 这里按策略优先级扫描整批请求，选择能处理任意请求的最高优先级策略。
     */
    private ToolExecutionStrategy selectStrategy(ToolExecutionContext context) {
        List<ToolExecutionRequest> requests = context.requests();
        if (requests == null || requests.isEmpty()) {
            // 没有请求，返回 NormalToolStrategy（默认策略）
            return findNormalToolStrategy();
        }

        for (ToolExecutionStrategy strategy : strategies) {
            if (requests.stream().anyMatch(strategy::supports)) {
                log.debug("[ExecuteToolNode] 选择策略: {} -> {}",
                        requests.stream().map(ToolExecutionRequest::name).toList(),
                        strategy.getClass().getSimpleName());
                return strategy;
            }
        }

        // 兜底：返回 NormalToolStrategy
        return findNormalToolStrategy();
    }

    /**
     * 查找 NormalToolStrategy（默认策略）
     */
    /** 兜底找到普通工具策略，保证任何批次至少有一个可执行路径。 */
    private ToolExecutionStrategy findNormalToolStrategy() {
        return strategies.stream()
                .filter(s -> s instanceof com.msz.resume.ai.chat.runtime.node.inner.strategy.NormalToolStrategy)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("NormalToolStrategy 未注册"));
    }

    /**
     * 构建状态更新（从 ToolExecutionResult）
     */
    private Map<String, Object> buildStateUpdate(ToolExecutionResult result, QueryLoopState currentState) {
        return buildStateUpdate(
                result.messages(),
                result.contexts(),
                result.transition(),
                result,
                currentState
        );
    }

    /**
     * 构建状态更新（原始参数）
     */
    private Map<String, Object> buildStateUpdate(
            List<ToolExecutionResultMessage> messages,
            List<ToolExecutionRequest> contexts,
            String transition,
            ToolExecutionResult result,
            QueryLoopState currentState) {

        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(QueryLoopState.MESSAGE_HISTORY, messages);
        stateUpdate.put(QueryLoopState.TOOL_USE_CONTEXT, contexts);
        stateUpdate.put(QueryLoopState.TRANSITION, transition);
        stateUpdate.put(QueryLoopState.OPENVIKING_IDENTITY, OpenVikingIdentitySupport.fromQueryLoopState(currentState));
        stateUpdate.put(QueryLoopState.TRACE_RUN_ID, currentState.getTraceRunId());
        stateUpdate.put(QueryLoopState.TRACE_AGENT_ID, currentState.getTraceAgentId());
        stateUpdate.put(QueryLoopState.TRACE_AGENT_LABEL, currentState.getTraceAgentLabel());
        stateUpdate.put(QueryLoopState.TRACE_AGENT_SCOPE, currentState.getTraceAgentScope());
        stateUpdate.put(QueryLoopState.REASONING_EFFORT, currentState.getReasoningEffort());

        // 如果有新发现的工具，更新 DISCOVERED_TOOLS
        if (result != null && !result.discoveredTools().equals(currentState.getDiscoveredTools())) {
            stateUpdate.put(QueryLoopState.DISCOVERED_TOOLS, result.discoveredTools());
        }

        // 如果任务计划发生变化，更新 TASK_PLAN（允许清空计划）
        if (result != null && result.taskPlan() != null
                && !result.taskPlan().equals(currentState.getTaskPlan())) {
            stateUpdate.put(QueryLoopState.TASK_PLAN, result.taskPlan());
        }

        return stateUpdate;
    }

    /**
     * Hook 预检结果
     */
    private record HookPreCheckResult(
            List<ToolExecutionRequest> activeRequests,
            List<ToolExecutionResultMessage> blockedResults,
            List<ToolExecutionRequest> blockedRequests
    ) {}
}
