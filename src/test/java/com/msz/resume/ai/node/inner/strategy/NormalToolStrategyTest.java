package com.msz.resume.ai.chat.runtime.node.inner.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.trace.ArtifactActionEventService;
import com.msz.resume.ai.chat.runtime.trace.AssistantCheckpointService;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamContext;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamEventSink;
import com.msz.resume.ai.chat.runtime.trace.TimelineActionService;
import com.msz.resume.ai.chat.runtime.trace.ToolActionEventService;
import com.msz.resume.ai.chat.runtime.trace.TraceAgentDescriptor;
import com.msz.resume.ai.chat.runtime.trace.TraceService;
import com.msz.resume.ai.chat.runtime.trace.langfuse.LangfuseTracingService;
import com.msz.resume.ai.chat.tooling.TaskPlanTool;
import com.msz.resume.ai.hook.HookContext;
import com.msz.resume.ai.hook.HookEngine;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NormalToolStrategyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final String SESSION_ID = "task-sync-session";

    @AfterEach
    void tearDown() {
        ChatStreamContext.clear(SESSION_ID);
        TaskPlanTool.clearTasks();
    }

    @Test
    @DisplayName("主 Agent 执行任务计划工具后应实时推送 task_update")
    void mainAgentTaskPlanToolShouldPublishTaskUpdate() throws Exception {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        ChatStreamContext.bind(SESSION_ID, new ChatStreamEventSink(emitter, OBJECT_MAPPER, SESSION_ID));

        strategy().execute(ToolExecutionContext.builder()
                .state(state(false))
                .agentDescriptor(TraceAgentDescriptor.mainAgent())
                .requests(List.of(createPlanRequest()))
                .blockedResults(List.of())
                .blockedRequests(List.of())
                .build());

        JsonNode taskUpdate = emitter.eventJsonByType("task_update");

        assertEquals(2, taskUpdate.path("payload").path("taskPlan").size());
        assertEquals("读取项目结构", taskUpdate.path("payload").path("taskPlan").get(0).path("description").asText());
        assertEquals(2, taskUpdate.path("payload").path("taskProgress").path("total").asInt());
        assertEquals(2, taskUpdate.path("payload").path("taskProgress").path("pending").asInt());
    }

    @Test
    @DisplayName("子 Agent 的隔离任务计划不应写入主任务栏")
    void subAgentTaskPlanToolShouldNotPublishTaskUpdate() {
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        ChatStreamContext.bind(SESSION_ID, new ChatStreamEventSink(emitter, OBJECT_MAPPER, SESSION_ID));

        strategy().execute(ToolExecutionContext.builder()
                .state(state(true))
                .agentDescriptor(new TraceAgentDescriptor("sub_1", "sub", "Explore #1", "Explore"))
                .requests(List.of(createPlanRequest()))
                .blockedResults(List.of())
                .blockedRequests(List.of())
                .build());

        assertFalse(emitter.hasEventType("task_update"));
    }

    private NormalToolStrategy strategy() {
        ToolRegistry registry = new ToolRegistry();
        registry.registerToolsFromObject(new TaskPlanTool());
        TimelineActionService timelineActionService = new TimelineActionService();
        HookEngine hookEngine = mock(HookEngine.class);
        when(hookEngine.postToolUse(any(HookContext.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        return new NormalToolStrategy(
                registry,
                hookEngine,
                new TraceService(),
                new ToolActionEventService(OBJECT_MAPPER, timelineActionService),
                new ArtifactActionEventService(OBJECT_MAPPER, timelineActionService),
                new AssistantCheckpointService(timelineActionService),
                new LangfuseTracingService(null, null, null, OBJECT_MAPPER)
        );
    }

    private QueryLoopState state(boolean subAgent) {
        Map<String, Object> input = new HashMap<>();
        input.put(QueryLoopState.SESSION_ID, SESSION_ID);
        input.put(QueryLoopState.IS_SUB_AGENT, subAgent);
        input.put(QueryLoopState.TASK_PLAN, List.of());
        return new QueryLoopState(input);
    }

    private ToolExecutionRequest createPlanRequest() {
        return ToolExecutionRequest.builder()
                .id("call_create_plan")
                .name("createPlan")
                .arguments("""
                        {"tasksJson":"[{\\\"description\\\":\\\"读取项目结构\\\",\\\"detail\\\":\\\"确认资源目录\\\"},{\\\"description\\\":\\\"生成简历\\\",\\\"detail\\\":\\\"写入工作台\\\"}]"}
                        """)
                .build();
    }

    private static class RecordingSseEmitter extends SseEmitter {
        private final List<JsonNode> events = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                Object data = item.getData();
                if (data instanceof String value && value.startsWith("{")) {
                    events.add(OBJECT_MAPPER.readTree(value));
                }
            }
        }

        JsonNode eventJsonByType(String type) {
            return events.stream()
                    .filter(event -> type.equals(event.path("type").asText()))
                    .findFirst()
                    .orElseThrow();
        }

        boolean hasEventType(String type) {
            return events.stream().anyMatch(event -> type.equals(event.path("type").asText()));
        }
    }
}
