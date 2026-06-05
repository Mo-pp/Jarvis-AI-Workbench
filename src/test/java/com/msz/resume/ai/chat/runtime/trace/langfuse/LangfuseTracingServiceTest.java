package com.msz.resume.ai.chat.runtime.trace.langfuse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.compression.model.CacheUsage;
import com.msz.resume.ai.chat.llm.config.LLMConfig;
import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.StepTraceEvent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LangfuseTracingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordContextRecallShouldExportTerminalSpanAndPropagateTraceAttributes() {
        TestHarness harness = newHarness();
        ChatRunTraceContext context = new ChatRunTraceContext("run-1", "session-1", null);

        harness.service.startTrace(context, "user-1", "hello");
        harness.service.recordStep(context, StepTraceEvent.builder()
                .id("step-1")
                .runId("run-1")
                .agentScope("main")
                .agentId("main")
                .agentLabel("Main Agent")
                .kind("context_recall")
                .name("openviking_recall")
                .title("OpenViking 自动召回")
                .op("injected")
                .status("success")
                .timestamp(Instant.now())
                .meta(Map.of("reason", "resource_keyword"))
                .build());
        harness.service.completeTrace(context, "done");

        List<SpanData> spans = harness.exporter.spans();
        SpanData recall = spanByName(spans, "openviking.recall");
        assertEquals("user-1", recall.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.user.id")));
        assertEquals("jarvis.chat.stream", recall.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.trace.name")));
        assertEquals(List.of("jarvis", "agent", "sse"),
                recall.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringArrayKey("langfuse.trace.tags")));
        assertEquals("run-1", recall.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.trace.metadata.run_id")));
    }

    @Test
    void completeTraceShouldCloseOpenCreatedToolSpan() {
        TestHarness harness = newHarness();
        ChatRunTraceContext context = new ChatRunTraceContext("run-2", "session-2", null);

        harness.service.startTrace(context, "user-2", "hello");
        harness.service.recordStep(context, StepTraceEvent.builder()
                .id("tool-1")
                .runId("run-2")
                .agentScope("main")
                .agentId("main")
                .agentLabel("Main Agent")
                .kind("tool_call")
                .name("askQuestionnaire")
                .title("askQuestionnaire")
                .op("created")
                .status("running")
                .timestamp(Instant.now())
                .meta(Map.of("toolCallId", "call-1"))
                .build());
        harness.service.completeTrace(context, "done");

        assertNotNull(spanByName(harness.exporter.spans(), "tool.askQuestionnaire"));
    }

    @Test
    void recordGenerationShouldUseLangfuseUsageDetailsKeys() throws Exception {
        TestHarness harness = newHarness();
        ChatRunTraceContext context = new ChatRunTraceContext("run-3", "session-3", null);
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("answer"))
                .tokenUsage(new TokenUsage(100, 25, 125))
                .build();

        harness.service.startTrace(context, "user-3", "hello");
        harness.service.recordGeneration(
                context,
                null,
                response,
                null,
                CacheUsage.of(100, 25, 40),
                false,
                "Main Agent",
                0,
                0
        );
        harness.service.completeTrace(context, "done");

        SpanData generation = spanByName(harness.exporter.spans(), "llm.call");
        String usageJson = generation.getAttributes()
                .get(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.observation.usage_details"));
        Map<String, Object> usage = objectMapper.readValue(usageJson, new TypeReference<>() {});
        assertEquals(100, usage.get("input"));
        assertEquals(25, usage.get("output"));
        assertEquals(125, usage.get("total"));
        assertEquals(40, usage.get("cache_read_input_tokens"));
    }

    @Test
    void recordFailedToolResultShouldNotWriteNullOutputAttribute() {
        TestHarness harness = newHarness();
        ChatRunTraceContext context = new ChatRunTraceContext("run-4", "session-4", null);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-failed")
                .name("brokenTool")
                .arguments("{}")
                .build();
        context.rememberToolStep("call-failed", "tool-failed");

        harness.service.startTrace(context, "user-4", "hello");
        harness.service.recordStep(context, StepTraceEvent.builder()
                .id("tool-failed")
                .runId("run-4")
                .agentScope("main")
                .agentId("main")
                .agentLabel("Main Agent")
                .kind("tool_call")
                .name("brokenTool")
                .title("brokenTool")
                .op("created")
                .status("running")
                .timestamp(Instant.now())
                .meta(Map.of("toolCallId", "call-failed"))
                .build());
        harness.service.recordToolResult(context, request, null, "failed", "tool exploded");
        harness.service.recordStep(context, StepTraceEvent.builder()
                .id("tool-failed")
                .runId("run-4")
                .agentScope("main")
                .agentId("main")
                .agentLabel("Main Agent")
                .kind("tool_call")
                .name("brokenTool")
                .title("brokenTool")
                .op("failed")
                .status("failed")
                .timestamp(Instant.now())
                .meta(Map.of("toolCallId", "call-failed"))
                .build());
        harness.service.failTrace(context, "failed");

        SpanData tool = spanByName(harness.exporter.spans(), "tool.brokenTool");
        assertNull(tool.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.observation.output")));
        assertEquals("failed", tool.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.observation.metadata.tool_status")));
        assertEquals("tool exploded", tool.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("langfuse.observation.status_message")));
    }

    private TestHarness newHarness() {
        InMemorySpanExporter exporter = new InMemorySpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Tracer tracer = tracerProvider.get("test-langfuse");
        LangfuseTraceProperties properties = new LangfuseTraceProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://cloud.langfuse.com");
        properties.setPublicKey("pk-test");
        properties.setSecretKey("sk-test");
        LLMConfig llmConfig = new LLMConfig();
        llmConfig.setProvider("dashscope");
        LangfuseTracingService service = new LangfuseTracingService(
                new SingleObjectProvider<>(tracer),
                properties,
                llmConfig,
                objectMapper
        );
        return new TestHarness(service, exporter);
    }

    private SpanData spanByName(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing span: " + name + ", exported=" + spans.stream().map(SpanData::getName).toList()));
    }

    private record TestHarness(LangfuseTracingService service, InMemorySpanExporter exporter) {
    }

    private record SingleObjectProvider<T>(T value) implements ObjectProvider<T> {
        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }
    }

    private static class InMemorySpanExporter implements SpanExporter {
        private final List<SpanData> spans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        List<SpanData> spans() {
            return spans;
        }
    }
}
