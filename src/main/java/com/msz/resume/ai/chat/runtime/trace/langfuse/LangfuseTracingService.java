package com.msz.resume.ai.chat.runtime.trace.langfuse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.compression.model.CacheUsage;
import com.msz.resume.ai.chat.compression.model.PipelineResult;
import com.msz.resume.ai.chat.llm.config.LLMConfig;
import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.StepTraceEvent;
import com.msz.resume.ai.chat.session.converter.ChatMessageTextExtractor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LangfuseTracingService {

    private static final int MAX_TEXT_CHARS = 2000;
    private static final int MAX_TOOL_ARGS_CHARS = 1200;
    private static final int MAX_TOOL_RESULT_CHARS = 1800;
    private static final String TRACE_NAME = "jarvis.chat.stream";
    private static final List<String> TRACE_TAGS = List.of("jarvis", "agent", "sse");

    private final ObjectProvider<Tracer> tracerProvider;
    private final LangfuseTraceProperties properties;
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final Map<String, TraceHandle> traces = new ConcurrentHashMap<>();
    private final Map<String, Span> stepSpans = new ConcurrentHashMap<>();

    public LangfuseTracingService(ObjectProvider<Tracer> tracerProvider,
                                  LangfuseTraceProperties properties,
                                  LLMConfig llmConfig,
                                  ObjectMapper objectMapper) {
        this.tracerProvider = tracerProvider;
        this.properties = properties;
        this.llmConfig = llmConfig;
        this.objectMapper = objectMapper;
    }

    public void startTrace(ChatRunTraceContext context, String userId, String userMessage) {
        if (!enabled(context)) {
            return;
        }
        Tracer tracer = tracer();
        if (tracer == null) {
            return;
        }
        Span span = tracer.spanBuilder(TRACE_NAME).startSpan();
        applyTraceAttributes(span, context, userId);
        span.setAttribute("langfuse.trace.input", truncate(userMessage, MAX_TEXT_CHARS));
        traces.put(context.runId(), new TraceHandle(span, Context.current().with(span), userId));
    }

    public void updateTraceInput(ChatRunTraceContext context, String userMessage) {
        TraceHandle handle = traceHandle(context);
        if (handle == null) {
            return;
        }
        handle.span().setAttribute("langfuse.trace.input", truncate(userMessage, MAX_TEXT_CHARS));
    }

    public void completeTrace(ChatRunTraceContext context, String output) {
        TraceHandle handle = traceHandle(context);
        if (handle == null) {
            return;
        }
        handle.span().setAttribute("langfuse.trace.output", truncate(output, MAX_TEXT_CHARS));
        handle.span().setStatus(StatusCode.OK);
        closeOpenStepSpans(context, StatusCode.OK, "trace completed");
        handle.span().end();
        traces.remove(context.runId());
    }

    public void failTrace(ChatRunTraceContext context, String error) {
        TraceHandle handle = traceHandle(context);
        if (handle == null) {
            return;
        }
        handle.span().setAttribute("langfuse.observation.level", "ERROR");
        handle.span().setAttribute("langfuse.observation.status_message", truncate(error, MAX_TEXT_CHARS));
        handle.span().recordException(new RuntimeException(error != null ? error : "Jarvis trace failed"));
        handle.span().setStatus(StatusCode.ERROR, error != null ? error : "failed");
        closeOpenStepSpans(context, StatusCode.ERROR, error != null ? error : "trace failed");
        handle.span().end();
        traces.remove(context.runId());
    }

    public void recordStep(ChatRunTraceContext context, StepTraceEvent event) {
        TraceHandle trace = traceHandle(context);
        if (trace == null || event == null) {
            return;
        }

        if ("created".equals(event.op()) && !"tool_call".equals(event.kind())) {
            return;
        }

        String key = stepKey(context.runId(), event.id());
        Span span = stepSpans.get(key);
        if (span == null) {
            span = startStepSpan(context, trace, event);
            stepSpans.put(key, span);
        }

        applyStepAttributes(span, context, event);
        if (isTerminal(event)) {
            if ("failed".equals(event.status()) || "blocked".equals(event.status())) {
                span.setStatus(StatusCode.ERROR, event.status());
            } else {
                span.setStatus(StatusCode.OK);
            }
            span.end();
            stepSpans.remove(key);
        }
    }

    public void recordCompression(ChatRunTraceContext context,
                                  PipelineResult pipelineResult,
                                  int messageCount,
                                  int projectedMessageCount) {
        TraceHandle trace = traceHandle(context);
        if (trace == null || pipelineResult == null) {
            return;
        }
        Span span = tracer().spanBuilder("context.compression")
                .setParent(trace.context())
                .startSpan();
        try {
            applyTraceAttributes(span, context, trace.userId());
            span.setAttribute("langfuse.observation.type", "span");
            span.setAttribute("langfuse.observation.metadata.compressed", pipelineResult.wasCompressed());
            span.setAttribute("langfuse.observation.metadata.original_tokens", pipelineResult.originalTokens());
            span.setAttribute("langfuse.observation.metadata.final_tokens", pipelineResult.finalTokens());
            span.setAttribute("langfuse.observation.metadata.tokens_saved", pipelineResult.tokensSaved());
            span.setAttribute("langfuse.observation.metadata.executed_levels", String.join(",", pipelineResult.executedLevels()));
            span.setAttribute("langfuse.observation.metadata.message_count", messageCount);
            span.setAttribute("langfuse.observation.metadata.projected_message_count", projectedMessageCount);
            span.setStatus(StatusCode.OK);
        } finally {
            span.end();
        }
    }

    public void recordGeneration(ChatRunTraceContext context,
                                 ChatRequest request,
                                 ChatResponse response,
                                 PipelineResult pipelineResult,
                                 CacheUsage cacheUsage,
                                 boolean subAgent,
                                 String agentLabel,
                                 long startedAtEpochMs,
                                 long durationMs) {
        TraceHandle trace = traceHandle(context);
        if (trace == null || response == null) {
            return;
        }
        long safeDurationMs = Math.max(durationMs, 0L);
        long endedAtEpochMs = startedAtEpochMs > 0 ? startedAtEpochMs + safeDurationMs : 0L;
        var spanBuilder = tracer().spanBuilder("llm.call")
                .setParent(trace.context());
        if (startedAtEpochMs > 0) {
            spanBuilder.setStartTimestamp(startedAtEpochMs, TimeUnit.MILLISECONDS);
        }
        Span span = spanBuilder.startSpan();
        try {
            AiMessage aiMessage = response.aiMessage();
            applyTraceAttributes(span, context, trace.userId());
            span.setAttribute("langfuse.observation.type", "generation");
            span.setAttribute("langfuse.observation.model.name", currentModelName());
            span.setAttribute("langfuse.observation.input", requestSummary(request));
            span.setAttribute("langfuse.observation.output", aiMessageSummary(aiMessage));
            span.setAttribute("langfuse.observation.usage_details", usageJson(response, cacheUsage));
            span.setAttribute("langfuse.observation.metadata.provider", safe(llmConfig.getProvider()));
            span.setAttribute("langfuse.observation.metadata.agent_label", safe(agentLabel));
            span.setAttribute("langfuse.observation.metadata.sub_agent", subAgent);
            span.setAttribute("langfuse.observation.metadata.duration_ms", safeDurationMs);
            if (pipelineResult != null) {
                span.setAttribute("langfuse.observation.metadata.compressed", pipelineResult.wasCompressed());
                span.setAttribute("langfuse.observation.metadata.original_tokens", pipelineResult.originalTokens());
                span.setAttribute("langfuse.observation.metadata.final_tokens", pipelineResult.finalTokens());
                span.setAttribute("langfuse.observation.metadata.executed_levels", String.join(",", pipelineResult.executedLevels()));
            }
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
        } finally {
            if (endedAtEpochMs > 0) {
                span.end(endedAtEpochMs, TimeUnit.MILLISECONDS);
            } else {
                span.end();
            }
        }
    }

    public void recordToolResult(ChatRunTraceContext context,
                                 ToolExecutionRequest request,
                                 String result,
                                 String status,
                                 String error) {
        Span span = currentToolSpan(context, request);
        if (span == null) {
            return;
        }
        if (result != null) {
            span.setAttribute("langfuse.observation.output", truncate(result, MAX_TOOL_RESULT_CHARS));
        }
        span.setAttribute("langfuse.observation.metadata.tool_status", safe(status));
        if (error != null && !error.isBlank()) {
            span.setAttribute("langfuse.observation.level", "ERROR");
            span.setAttribute("langfuse.observation.status_message", truncate(error, MAX_TEXT_CHARS));
        }
    }

    private Span startStepSpan(ChatRunTraceContext context, TraceHandle trace, StepTraceEvent event) {
        String name = switch (event.kind()) {
            case "tool_call" -> "tool." + safe(event.name());
            case "context_recall" -> "openviking.recall";
            case "sub_agent" -> "sub_agent." + safe(event.agentLabel());
            case "tool_batch" -> "tool.batch";
            case "llm" -> "agent." + safe(event.agentLabel());
            default -> safe(event.kind()) + "." + safe(event.name());
        };
        Context parent = trace.context();
        if (event.parentId() != null && !event.parentId().isBlank()) {
            Span parentSpan = stepSpans.get(stepKey(context.runId(), event.parentId()));
            if (parentSpan != null) {
                parent = trace.context().with(parentSpan);
            }
        }
        return tracer().spanBuilder(name)
                .setParent(parent)
                .startSpan();
    }

    private void applyStepAttributes(Span span, ChatRunTraceContext context, StepTraceEvent event) {
        TraceHandle trace = traceHandle(context);
        applyTraceAttributes(span, context, trace != null ? trace.userId() : null);
        span.setAttribute("langfuse.observation.type", "span");
        span.setAttribute("langfuse.observation.metadata.step_id", safe(event.id()));
        span.setAttribute("langfuse.observation.metadata.parent_step_id", safe(event.parentId()));
        span.setAttribute("langfuse.observation.metadata.kind", safe(event.kind()));
        span.setAttribute("langfuse.observation.metadata.name", safe(event.name()));
        span.setAttribute("langfuse.observation.metadata.title", safe(event.title()));
        span.setAttribute("langfuse.observation.metadata.op", safe(event.op()));
        span.setAttribute("langfuse.observation.metadata.status", safe(event.status()));
        span.setAttribute("langfuse.observation.metadata.agent_id", safe(event.agentId()));
        span.setAttribute("langfuse.observation.metadata.agent_scope", safe(event.agentScope()));
        span.setAttribute("langfuse.observation.metadata.agent_label", safe(event.agentLabel()));
        if (event.meta() != null) {
            for (Map.Entry<String, Object> entry : event.meta().entrySet()) {
                setMetadataAttribute(span, "langfuse.observation.metadata." + entry.getKey(), entry.getValue());
            }
            if ("tool_call".equals(event.kind())) {
                span.setAttribute("langfuse.observation.input", metadataJson(event.meta()));
            }
        }
    }

    private void applyTraceAttributes(Span span, ChatRunTraceContext context, String userId) {
        span.setAttribute("langfuse.trace.name", TRACE_NAME);
        span.setAttribute("langfuse.session.id", safe(context.sessionId()));
        span.setAttribute("langfuse.user.id", safe(userId));
        span.setAttribute("langfuse.environment", safe(properties.getEnvironment()));
        span.setAttribute("langfuse.release", safe(properties.getRelease()));
        span.setAttribute("langfuse.trace.metadata.run_id", safe(context.runId()));
        span.setAttribute("langfuse.trace.metadata.feature", "chat_stream");
        span.setAttribute(AttributeKey.stringArrayKey("langfuse.trace.tags"), TRACE_TAGS);
    }

    private Span currentToolSpan(ChatRunTraceContext context, ToolExecutionRequest request) {
        if (context == null || request == null) {
            return null;
        }
        String stepId = context.findToolStep(toolCallKey(request));
        if (stepId == null || stepId.isBlank()) {
            return null;
        }
        return stepSpans.get(stepKey(context.runId(), stepId));
    }

    private TraceHandle traceHandle(ChatRunTraceContext context) {
        if (context == null || context.runId() == null) {
            return null;
        }
        return traces.get(context.runId());
    }

    private boolean enabled(ChatRunTraceContext context) {
        return properties.configured() && context != null && context.runId() != null;
    }

    private Tracer tracer() {
        return tracerProvider.getIfAvailable();
    }

    private boolean isTerminal(StepTraceEvent event) {
        return ("context_recall".equals(event.kind()) && !"created".equals(event.op()))
                || "completed".equals(event.op())
                || "failed".equals(event.op())
                || "blocked".equals(event.op())
                || "pending".equals(event.op())
                || "success".equals(event.status())
                || "failed".equals(event.status())
                || "blocked".equals(event.status())
                || "pending".equals(event.status())
                || "skipped".equals(event.status());
    }

    private String currentModelName() {
        String provider = llmConfig.getProvider();
        if ("zhipu".equalsIgnoreCase(provider)) {
            return llmConfig.getZhipu().getModel();
        }
        if ("dashscope".equalsIgnoreCase(provider)) {
            return llmConfig.getDashscope().getModel();
        }
        if ("gpt".equalsIgnoreCase(provider)) {
            return llmConfig.getGpt().getModel();
        }
        if ("qianfan-coding-plan".equalsIgnoreCase(provider)) {
            return llmConfig.getQianfanCodingPlan().getModel();
        }
        return provider;
    }

    private String requestSummary(ChatRequest request) {
        if (request == null || request.messages() == null) {
            return "{}";
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("messageCount", request.messages().size());
        summary.put("messages", request.messages().stream().map(this::messageSummary).toList());
        summary.put("toolCount", request.toolSpecifications() != null ? request.toolSpecifications().size() : 0);
        return toJson(summary);
    }

    private Map<String, Object> messageSummary(ChatMessage message) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", message.type().name());
        String text = switch (message) {
            case UserMessage userMessage -> ChatMessageTextExtractor.userText(userMessage);
            case SystemMessage systemMessage -> "[system prompt omitted]";
            case AiMessage aiMessage -> aiMessage.text();
            case ToolExecutionResultMessage toolMessage -> toolMessage.toolName() + ": " + toolMessage.text();
            default -> String.valueOf(message);
        };
        summary.put("text", truncate(text, MAX_TEXT_CHARS));
        return summary;
    }

    private String aiMessageSummary(AiMessage aiMessage) {
        if (aiMessage == null) {
            return "";
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("text", truncate(aiMessage.text(), MAX_TEXT_CHARS));
        summary.put("hasToolCalls", aiMessage.hasToolExecutionRequests());
        if (aiMessage.hasToolExecutionRequests()) {
            summary.put("toolCalls", aiMessage.toolExecutionRequests().stream()
                    .map(request -> Map.of(
                            "id", safe(request.id()),
                            "name", safe(request.name()),
                            "arguments", truncate(request.arguments(), MAX_TOOL_ARGS_CHARS)
                    ))
                    .toList());
        }
        return toJson(summary);
    }

    private String usageJson(ChatResponse response, CacheUsage cacheUsage) {
        Map<String, Object> usage = new LinkedHashMap<>();
        if (response.tokenUsage() != null) {
            usage.put("input", response.tokenUsage().inputTokenCount() != null ? response.tokenUsage().inputTokenCount() : 0);
            usage.put("output", response.tokenUsage().outputTokenCount() != null ? response.tokenUsage().outputTokenCount() : 0);
            usage.put("total", response.tokenUsage().totalTokenCount() != null ? response.tokenUsage().totalTokenCount() : 0);
        }
        if (cacheUsage != null && cacheUsage.cachedTokens() > 0) {
            usage.put("cache_read_input_tokens", cacheUsage.cachedTokens());
        }
        return toJson(usage);
    }

    private String metadataJson(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return "{}";
        }
        Map<String, Object> safeMeta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            Object value = entry.getValue();
            safeMeta.put(entry.getKey(), value instanceof String text ? truncate(text, MAX_TOOL_ARGS_CHARS) : value);
        }
        return toJson(safeMeta);
    }

    private void setMetadataAttribute(Span span, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean bool) {
            span.setAttribute(key, bool);
        } else if (value instanceof Integer integer) {
            span.setAttribute(key, integer.longValue());
        } else if (value instanceof Long longValue) {
            span.setAttribute(key, longValue);
        } else if (value instanceof Number number) {
            span.setAttribute(key, number.doubleValue());
        } else {
            span.setAttribute(key, truncate(String.valueOf(value), MAX_TOOL_ARGS_CHARS));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String toolCallKey(ToolExecutionRequest request) {
        if (request != null && request.id() != null && !request.id().isBlank()) {
            return request.id();
        }
        return request != null
                ? request.name() + "_" + Integer.toHexString(System.identityHashCode(request))
                : "unknown_tool";
    }

    private String stepKey(String runId, String stepId) {
        return runId + ":" + stepId;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private void closeOpenStepSpans(ChatRunTraceContext context, StatusCode statusCode, String statusMessage) {
        if (context == null || context.runId() == null) {
            return;
        }
        String prefix = context.runId() + ":";
        stepSpans.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) {
                return false;
            }
            Span span = entry.getValue();
            span.setStatus(statusCode, statusMessage);
            span.end();
            return true;
        });
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private record TraceHandle(Span span, Context context, String userId) {
    }
}
