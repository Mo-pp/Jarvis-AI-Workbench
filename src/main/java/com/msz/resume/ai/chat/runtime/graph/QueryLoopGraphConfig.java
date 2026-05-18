package com.msz.resume.ai.chat.runtime.graph;


import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentitySupport;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.chat.runtime.node.inner.CallLlmNode;
import com.msz.resume.ai.chat.runtime.node.inner.ErrorRecoveryNode;
import com.msz.resume.ai.chat.runtime.node.inner.ExecuteToolNode;
import com.msz.resume.ai.chat.runtime.node.inner.strategy.ToolExecutionResult;
import com.msz.resume.ai.chat.runtime.state.serialization.QueryLoopStateSerializer;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;


/**
 * 内层小循环的图配置
 * 把所有的节点组装成一个循环的图
 *
 * 流转逻辑（对齐 Claude Code Agent Loop 设计）：
 * call_llm → afterLlmRoute → execute_tool / error_recovery / call_llm(retry) / END
 * execute_tool → call_llm（不管成功失败，都把结果/错误信息给LLM，由LLM决定下一步）
 * error_recovery → afterErrorRecoveryRoute → call_llm(retry) / END
 *
 * 设计说明：
 * - 纯文本回复 = 任务完成，直接结束（对齐 Claude Code 设计）
 * - 不再使用 Nudge 催促机制，依赖系统提示词引导 LLM 正确使用工具
 */

@Slf4j
@Configuration
public class QueryLoopGraphConfig {

    @Bean
    public StateGraph<QueryLoopState> queryLoopGraph(
            CallLlmNode callLlmNode,
            ExecuteToolNode executeToolNode,
            ErrorRecoveryNode errorRecoveryNode
    ) throws Exception{

        // ---------------------- call_llm 之后的路由 ----------------------
        // 分支1：纯文本回复 → end（任务完成，对齐 Claude Code 设计）
        // 分支2：有工具调用 → execute_tool
        // 分支3：LLM调用异常(error) → error_recovery
        // 分支4：需要重试(retry) → call_llm
        // 分支5：不可恢复(terminate) → end
        AsyncEdgeAction<QueryLoopState> afterLlmRoute = new AsyncEdgeAction<QueryLoopState>() {
            @Override
            public CompletableFuture<String> apply(QueryLoopState currentState) {
                OpenVikingIdentity identity = OpenVikingIdentitySupport.fromQueryLoopState(currentState);
                return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
                    String transition = currentState.getTransition();

                    // 不可恢复错误，直接结束
                    if ("terminate".equals(transition)) {
                        return "end";
                    }

                    // LLM调用异常，进入错误恢复
                    if ("error".equals(transition)) {
                        return "error_recovery";
                    }

                    // 重试，回到call_llm
                    if ("retry".equals(transition)) {
                        return "call_llm";
                    }

                    // 子Agent模式：轮次超限，直接结束
                    if (currentState.isSubAgent()) {
                        int maxTurns = currentState.getMaxTurns();
                        if (maxTurns > 0 && currentState.getTurnCount() >= maxTurns) {
                            log.info("[afterLlmRoute] 子Agent达到最大轮次限制: {}/{}", currentState.getTurnCount(), maxTurns);
                            return "end";
                        }
                    }

                    // 正常判断：是否有工具调用
                    List<ChatMessage> messages = currentState.getMessages();
                    ChatMessage lastMessage = messages.getLast();
                    if (lastMessage instanceof AiMessage aiMessage) {
                        if (aiMessage.hasToolExecutionRequests()) {
                            return "execute_tool";
                        }
                    }

                    // 纯文本回复 = 任务完成，直接结束
                    // 对齐 Claude Code 设计：纯文本回复意味着 LLM 认为任务已完成
                    log.info("[afterLlmRoute] LLM返回纯文本，任务完成");
                    return "end";
                });
            }
        };

        // ---------------------- error_recovery 之后的路由 ----------------------
        // 可恢复(retry) → call_llm（重新调用大模型）
        // 不可恢复(terminate) → end（终止循环）
        AsyncEdgeAction<QueryLoopState> afterErrorRecoveryRoute = new AsyncEdgeAction<QueryLoopState>() {
            @Override
            public CompletableFuture<String> apply(QueryLoopState currentState) {
                OpenVikingIdentity identity = OpenVikingIdentitySupport.fromQueryLoopState(currentState);
                return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
                    String transition = currentState.getTransition();

                    // 可恢复，重试
                    if ("retry".equals(transition)) {
                        return "call_llm";
                    }

                    // 不可恢复，终止
                    return "end";
                });
            }
        };

        // ---------------------- execute_tool 之后的路由 ----------------------
        // 正常执行(tool_executed_success/tool_executed_failed) → call_llm
        // artifact_ready → end（前端产物已就绪）
        AsyncEdgeAction<QueryLoopState> afterExecuteToolRoute = new AsyncEdgeAction<QueryLoopState>() {
            @Override
            public CompletableFuture<String> apply(QueryLoopState currentState) {
                OpenVikingIdentity identity = OpenVikingIdentitySupport.fromQueryLoopState(currentState);
                return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
                    String transition = currentState.getTransition();

                    if (ToolExecutionResult.TRANSITION_ARTIFACT_READY.equals(transition)) {
                        log.info("[afterExecuteToolRoute] 工具已产出前端 artifact，本轮结束");
                        return "end";
                    }

                    // 正常情况：回到 call_llm 让 LLM 决定下一步
                    return "call_llm";
                });
            }
        };

        // ---------------------- 组装图 ----------------------
        StateGraph<QueryLoopState> graph = new StateGraph<>(QueryLoopState.SCHEMA, new QueryLoopStateSerializer());

        // 添加节点
        graph.addNode("call_llm", callLlmNode);
        graph.addNode("execute_tool", executeToolNode);
        graph.addNode("error_recovery", errorRecoveryNode);

        // 定义跳转边
        graph.addEdge(START, "call_llm");

        // call_llm 之后的多路分支（纯文本回复直接结束）
        graph.addConditionalEdges("call_llm", afterLlmRoute, Map.of(
                "execute_tool", "execute_tool",
                "error_recovery", "error_recovery",
                "call_llm", "call_llm",
                "end", END
        ));

        // execute_tool 之后：正常回到call_llm，artifact_ready 则结束
        graph.addConditionalEdges("execute_tool", afterExecuteToolRoute, Map.of(
                "call_llm", "call_llm",
                "end", END
        ));

        // error_recovery 之后的两路分支
        graph.addConditionalEdges("error_recovery", afterErrorRecoveryRoute, Map.of(
                "call_llm", "call_llm",
                "end", END
        ));

        return graph;
    }
}
