package com.msz.resume.ai.chat.runtime.graph;


import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentitySupport;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.node.outer.SessionInitNode;
import com.msz.resume.ai.chat.runtime.node.outer.UsageStatNode;
import com.msz.resume.ai.chat.runtime.state.serialization.SessionStateSerializer;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 外层会话管理的图配置
 * 单轮用户请求对应一次完整的线性流转：session_init → run_inner_loop → usage_stat → END
 * 多轮对话由 Controller 层通过 sessionId 缓存实现，不在图内循环
 */
@Slf4j
@Configuration
public class QueryEngineGraphConfig {

    private static final int INNER_LOOP_RECURSION_LIMIT = 100;

    @Bean
    public CompiledGraph<SessionState> queryEngineGraph(
            SessionInitNode sessionInitNode,
            UsageStatNode usageStatNode,
            StateGraph<QueryLoopState> queryLoopGraph
    )throws Exception{
        AsyncNodeAction<SessionState> runInnerLoop =new AsyncNodeAction<SessionState>() {

            @Override
            public CompletableFuture<Map<String, Object>> apply(SessionState sessionState) {
                OpenVikingIdentity identity = OpenVikingIdentitySupport.fromSessionState(sessionState);
                return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
                    QueryLoopState innerState = sessionState.<QueryLoopState>value(SessionState.INNER_STATE).orElseThrow();

                    Map<String, Object> innerInput = new HashMap<>();
                    innerInput.put(QueryLoopState.MESSAGE_HISTORY, innerState.getMessages());
                    innerInput.put(QueryLoopState.USER_CONTEXT, sessionState.getUserContext());
                    innerInput.put(QueryLoopState.OPENVIKING_IDENTITY, identity);
                    innerInput.put(QueryLoopState.SESSION_ID, sessionState.getSessionId());
                    innerInput.put(QueryLoopState.TASK_PLAN, innerState.getTaskPlan());
                    innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, innerState.getSurfacedOpenVikingUris());
                    innerInput.put(QueryLoopState.TRACE_RUN_ID, innerState.getTraceRunId());
                    innerInput.put(QueryLoopState.TRACE_AGENT_ID, innerState.getTraceAgentId());
                    innerInput.put(QueryLoopState.TRACE_AGENT_LABEL, innerState.getTraceAgentLabel());
                    innerInput.put(QueryLoopState.TRACE_AGENT_SCOPE, innerState.getTraceAgentScope());

                    CompiledGraph<QueryLoopState> compiledInner = null;
                    try {
                        CompileConfig innerCompileConfig = CompileConfig.builder()
                                .recursionLimit(INNER_LOOP_RECURSION_LIMIT)
                                .build();
                        compiledInner = queryLoopGraph.compile(innerCompileConfig);

                        QueryLoopState finalInnerState = null;
                        for (var output : compiledInner.stream(innerInput)) {
                            log.info("[内层步骤] " + output.node() + ", 状态: " + output.state());
                            finalInnerState = output.state();
                        }
                        Map<String, Object> update = new HashMap<>();
                        update.put(SessionState.INNER_STATE, finalInnerState);
                        update.put(SessionState.OPENVIKING_IDENTITY, identity);
                        return update;

                    } catch (GraphStateException e) {
                        log.error("[内层循环执行失败] 会话ID: {}", sessionState.getSessionId(), e);
                        throw new RuntimeException("内层循环编译失败", e);
                    }
                });
            }
        };

        StateGraph<SessionState> workflow = new StateGraph<>(SessionState.SCHEMA, new SessionStateSerializer());

        workflow.addNode("session_init", sessionInitNode);
        workflow.addNode("run_inner_loop", runInnerLoop);
        workflow.addNode("usage_stat", usageStatNode);

        workflow.addEdge(START, "session_init");
        workflow.addEdge("session_init", "run_inner_loop");
        workflow.addEdge("run_inner_loop", "usage_stat");
        workflow.addEdge("usage_stat", END);

        CompileConfig config = CompileConfig.builder()
                .build();

        return workflow.compile(config);
    }
}
