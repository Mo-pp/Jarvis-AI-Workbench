package com.msz.resume.ai.chat.runtime.trace;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceServiceTest {

    @Test
    @DisplayName("主 Agent 工具链应保持 llm -> tool_batch -> tool_call 的稳定父子关系")
    void shouldKeepStableParentChainForMainAgentToolCalls() {
        TraceService traceService = new TraceService();
        RecordingSink sink = new RecordingSink();
        ChatRunTraceContext context = new ChatRunTraceContext("run-1", "session-1", sink);
        TraceAgentDescriptor mainAgent = TraceAgentDescriptor.mainAgent();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("getCurrentTime")
                .arguments("{}")
                .build();

        traceService.startLlmRound(context, mainAgent);
        String batchId = traceService.startToolBatch(context, mainAgent, List.of(request));
        String toolId = traceService.planToolCall(context, mainAgent, batchId, request);
        String ensuredBatchId = traceService.ensureToolBatch(context, mainAgent, List.of(request));
        traceService.startToolCall(context, mainAgent, request);
        traceService.completeToolCall(context, mainAgent, request);
        traceService.completeToolBatch(context, mainAgent, ensuredBatchId);

        List<StepTraceEvent> events = sink.events;
        StepTraceEvent llm = events.get(0);
        StepTraceEvent batchStarted = events.get(1);
        StepTraceEvent toolCreated = events.get(2);
        StepTraceEvent toolStarted = events.get(3);
        StepTraceEvent toolCompleted = events.get(4);
        StepTraceEvent batchCompleted = events.get(5);

        assertEquals("llm", llm.kind());
        assertEquals(llm.id(), batchStarted.parentId());
        assertEquals(batchId, ensuredBatchId);
        assertEquals(batchId, batchStarted.id());
        assertEquals(batchId, batchCompleted.id());
        assertEquals(toolId, toolCreated.id());
        assertEquals(toolId, toolStarted.id());
        assertEquals(toolId, toolCompleted.id());
        assertEquals(batchId, toolCreated.parentId());
        assertEquals(batchId, toolStarted.parentId());
        assertEquals(batchId, toolCompleted.parentId());
        assertEquals("success", toolCompleted.status());
        assertEquals("success", batchCompleted.status());
    }

    @Test
    @DisplayName("缺失 toolCallId 时 trace 仍应创建稳定工具步骤而不抛异常")
    void shouldHandleToolCallsWithoutId() {
        TraceService traceService = new TraceService();
        RecordingSink sink = new RecordingSink();
        ChatRunTraceContext context = new ChatRunTraceContext("run-2", "session-2", sink);
        TraceAgentDescriptor mainAgent = TraceAgentDescriptor.mainAgent();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("customTool")
                .arguments("{}")
                .build();

        traceService.startLlmRound(context, mainAgent);
        String batchId = traceService.startToolBatch(context, mainAgent, List.of(request));
        String toolId = traceService.planToolCall(context, mainAgent, batchId, request);
        traceService.startToolCall(context, mainAgent, request);
        traceService.completeToolCall(context, mainAgent, request);

        List<StepTraceEvent> events = sink.events;
        StepTraceEvent toolCreated = events.get(2);
        StepTraceEvent toolStarted = events.get(3);
        StepTraceEvent toolCompleted = events.get(4);

        assertEquals(toolId, toolCreated.id());
        assertEquals(toolId, toolStarted.id());
        assertEquals(toolId, toolCompleted.id());
        assertEquals(batchId, toolStarted.parentId());
        assertEquals("success", toolCompleted.status());
    }

    @Test
    @DisplayName("OpenViking 自动召回应作为可见 trace step 发布")
    void shouldPublishContextRecallStep() {
        TraceService traceService = new TraceService();
        RecordingSink sink = new RecordingSink();
        ChatRunTraceContext context = new ChatRunTraceContext("run-3", "session-3", sink);
        TraceAgentDescriptor mainAgent = TraceAgentDescriptor.mainAgent();

        traceService.startLlmRound(context, mainAgent);
        traceService.recordContextRecall(
                context,
                mainAgent,
                "triggered",
                "running",
                Map.of("reason", "resource_keyword", "targetScopes", List.of("resource"))
        );

        List<StepTraceEvent> events = sink.events;
        StepTraceEvent llm = events.get(0);
        StepTraceEvent recall = events.get(1);

        assertEquals("context_recall", recall.kind());
        assertEquals("openviking_recall", recall.name());
        assertEquals("OpenViking 自动召回", recall.title());
        assertEquals("triggered", recall.op());
        assertEquals("running", recall.status());
        assertEquals(llm.id(), recall.parentId());
        assertEquals("resource_keyword", recall.meta().get("reason"));
    }

    private static class RecordingSink implements TracePublisher {
        private final List<StepTraceEvent> events = new ArrayList<>();

        @Override
        public void publishStep(StepTraceEvent event) {
            events.add(event);
        }
    }
}
