package com.msz.resume.ai.chat.runtime.node.inner.strategy;

import com.msz.resume.ai.chat.runtime.subagent.SubGraphNode;
import com.msz.resume.ai.chat.runtime.trace.DelegationActionEventService;
import com.msz.resume.ai.chat.runtime.trace.TraceService;
import com.msz.resume.ai.chat.runtime.trace.langfuse.LangfuseTracingService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class SpawnAgentStrategyTest {

    @Test
    @DisplayName("ResumeBusinessExplore 未传 maxTurns 时默认50轮")
    void resumeBusinessExploreShouldDefaultToFiftyTurns() throws Exception {
        Object params = parseParams("""
                {"prompt":"探索项目业务证据","subagentType":"ResumeBusinessExplore"}
                """);

        assertEquals(50, maxTurns(params));
    }

    @Test
    @DisplayName("普通子Agent未传 maxTurns 时仍默认30轮")
    void generalShouldKeepThirtyTurnsDefault() throws Exception {
        Object params = parseParams("""
                {"prompt":"普通探索","subagentType":"Explore"}
                """);

        assertEquals(30, maxTurns(params));
    }

    @Test
    @DisplayName("显式传入 maxTurns 时应保留用户/模型指定值")
    void explicitMaxTurnsShouldBeRespected() throws Exception {
        Object params = parseParams("""
                {"prompt":"复杂探索","subagentType":"ResumeBusinessExplore","maxTurns":60}
                """);

        assertEquals(60, maxTurns(params));
    }

    private Object parseParams(String arguments) throws Exception {
        SpawnAgentStrategy strategy = new SpawnAgentStrategy(
                mock(NormalToolStrategy.class),
                mock(SubGraphNode.class),
                mock(TraceService.class),
                mock(DelegationActionEventService.class),
                mock(LangfuseTracingService.class)
        );
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("spawnAgent")
                .arguments(arguments)
                .build();

        Method method = SpawnAgentStrategy.class.getDeclaredMethod("parseSpawnAgentParams", ToolExecutionRequest.class);
        method.setAccessible(true);
        return method.invoke(strategy, request);
    }

    private int maxTurns(Object params) throws Exception {
        Method method = params.getClass().getDeclaredMethod("maxTurns");
        method.setAccessible(true);
        return (int) method.invoke(params);
    }
}
