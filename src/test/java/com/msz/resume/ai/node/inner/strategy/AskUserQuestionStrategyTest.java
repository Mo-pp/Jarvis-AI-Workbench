package com.msz.resume.ai.chat.runtime.node.inner.strategy;

import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.TraceAgentDescriptor;
import com.msz.resume.ai.chat.runtime.trace.TraceService;
import com.msz.resume.ai.chat.tooling.AskUserQuestionParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AskUserQuestionStrategyTest {

    private final AskUserQuestionStrategy strategy = new AskUserQuestionStrategy(new AskUserQuestionParser());

    @Test
    @DisplayName("混合 AskUserQuestion 批次会为每个 tool call 返回结果，避免历史协议不完整")
    void mixedBatchReturnsResultForEveryToolCall() {
        ToolExecutionRequest normalRequest = ToolExecutionRequest.builder()
                .id("call_time")
                .name("getCurrentTime")
                .arguments("{}")
                .build();
        ToolExecutionRequest askRequest = ToolExecutionRequest.builder()
                .id("call_question")
                .name("askUserQuestion")
                .arguments("""
                        {"questionText":"你的目标岗位是什么？","questionType":"text"}
                        """)
                .build();
        ToolExecutionContext context = ToolExecutionContext.builder()
                .state(new QueryLoopState(new HashMap<>()))
                .requests(List.of(normalRequest, askRequest))
                .blockedResults(List.of())
                .blockedRequests(List.of())
                .build();

        ToolExecutionResult result = strategy.execute(context);

        assertEquals(ToolExecutionResult.TRANSITION_ARTIFACT_READY, result.transition());
        assertEquals(2, result.messages().size());
        assertEquals("call_time", result.messages().get(0).id());
        assertTrue(result.messages().get(0).text().contains("工具未执行"));
        assertEquals("call_question", result.messages().get(1).id());
        assertTrue(result.messages().get(1).text().contains("\"type\":\"questionnaire\""));
        assertEquals(List.of(normalRequest, askRequest), result.contexts());
    }

    @Test
    @DisplayName("AskUserQuestion 策略应补齐工具执行 trace 终态")
    void shouldPublishTraceLifecycleForAskToolCalls() {
        ChatRunTraceContext traceContext = new ChatRunTraceContext("run-question", "session-question", null);
        TraceService traceService = mock(TraceService.class);
        TraceAgentDescriptor agent = TraceAgentDescriptor.mainAgent();
        ToolExecutionRequest askRequest = ToolExecutionRequest.builder()
                .id("call_question")
                .name("askUserQuestion")
                .arguments("""
                        {"questionText":"你的目标岗位是什么？","questionType":"text"}
                        """)
                .build();
        AskUserQuestionStrategy tracedStrategy = new AskUserQuestionStrategy(
                new AskUserQuestionParser(),
                traceService,
                null
        );
        ToolExecutionContext context = ToolExecutionContext.builder()
                .state(new QueryLoopState(new HashMap<>()))
                .traceContext(traceContext)
                .agentDescriptor(agent)
                .requests(List.of(askRequest))
                .blockedResults(List.of())
                .blockedRequests(List.of())
                .build();

        tracedStrategy.execute(context);

        verify(traceService).startToolCall(traceContext, agent, askRequest);
        verify(traceService).completeToolCall(traceContext, agent, askRequest);
    }
}
