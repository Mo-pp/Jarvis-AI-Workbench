package com.msz.resume.ai.chat.runtime.node.inner;

import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentitySupport;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 子Agent探索轮次耗尽后的强制收束节点。
 *
 * <p>它不执行任何工具，只把未执行的工具调用补成跳过结果，并给模型追加一条明确的最终总结指令。
 */
@Slf4j
@Component
public class SubAgentWrapUpNode implements AsyncNodeAction<QueryLoopState> {

    public static final int DEFAULT_WRAP_UP_MAX_TURNS = 5;

    @Override
    public CompletableFuture<Map<String, Object>> apply(QueryLoopState currentState) {
        OpenVikingIdentity identity = OpenVikingIdentitySupport.fromQueryLoopState(currentState);
        return OpenVikingIdentitySupport.supplyAsync(identity, () -> {
            List<ChatMessage> messages = currentState.getMessages();
            List<ChatMessage> additions = new ArrayList<>();

            if (!messages.isEmpty() && messages.getLast() instanceof AiMessage aiMessage
                    && aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    additions.add(ToolExecutionResultMessage.from(
                            request,
                            "Skipped because the sub-agent reached its exploration turn budget. "
                                    + "Use already collected evidence and produce the final structured result now."
                    ));
                }
            }

            additions.add(UserMessage.from("""
                    You have reached the exploration turn budget. Stop exploring and do not call tools.
                    Use only the evidence already collected in this sub-agent context.
                    Return the final answer now in the structure requested by the original task.
                    If evidence is incomplete, still return the best candidates and mark gaps, risks, and inferred parts explicitly.
                    """));

            boolean alreadyWrappingUp = currentState.isSubAgentWrapUp();
            int wrapUpMaxTurns = currentState.getSubAgentWrapUpMaxTurns();
            if (wrapUpMaxTurns <= 0) {
                wrapUpMaxTurns = DEFAULT_WRAP_UP_MAX_TURNS;
            }
            int targetMaxTurns = alreadyWrappingUp && currentState.getMaxTurns() > 0
                    ? currentState.getMaxTurns()
                    : currentState.getTurnCount() + wrapUpMaxTurns;

            Map<String, Object> update = new HashMap<>();
            update.put(QueryLoopState.MESSAGE_HISTORY, additions);
            update.put(QueryLoopState.SUB_AGENT_WRAP_UP, true);
            update.put(QueryLoopState.MAX_TURNS, targetMaxTurns);
            update.put(QueryLoopState.SUB_AGENT_WRAP_UP_MAX_TURNS, wrapUpMaxTurns);
            update.put(QueryLoopState.TRANSITION, "sub_agent_wrap_up");
            update.put(QueryLoopState.OPENVIKING_IDENTITY, identity);
            update.put(QueryLoopState.TRACE_RUN_ID, currentState.getTraceRunId());
            update.put(QueryLoopState.TRACE_AGENT_ID, currentState.getTraceAgentId());
            update.put(QueryLoopState.TRACE_AGENT_LABEL, currentState.getTraceAgentLabel());
            update.put(QueryLoopState.TRACE_AGENT_SCOPE, currentState.getTraceAgentScope());
            update.put(QueryLoopState.REASONING_EFFORT, currentState.getReasoningEffort());
            log.info("[SubAgentWrapUpNode] 子Agent进入强制收束: turns={}, maxTurns={}, wrapUpMaxTurns={}",
                    currentState.getTurnCount(), targetMaxTurns, wrapUpMaxTurns);
            return update;
        });
    }
}
