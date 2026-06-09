package com.msz.resume.ai.tool.impl;

import com.msz.resume.ai.chat.tooling.SpawnAgentTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnAgentToolTest {

    @Test
    @DisplayName("spawnAgent 描述应暴露 ResumeBusinessExplore 类型")
    void spawnAgentDescriptionShouldExposeResumeBusinessExploreType() {
        ToolSpecification spec = ToolSpecifications.toolSpecificationsFrom(new SpawnAgentTool()).stream()
                .filter(tool -> "spawnAgent".equals(tool.name()))
                .findFirst()
                .orElseThrow();

        String description = spec.description();

        assertTrue(description.contains("ResumeBusinessExplore"));
        assertTrue(description.contains("简历项目业务探索"));
        assertTrue(description.contains("用户痛点"));
        assertTrue(description.contains("X占位指标"));
        assertTrue(description.contains("构造优化故事"));
        assertTrue(description.contains("减少架构名词堆砌"));
        assertTrue(description.contains("不直接生成最终简历artifact"));
    }
}
