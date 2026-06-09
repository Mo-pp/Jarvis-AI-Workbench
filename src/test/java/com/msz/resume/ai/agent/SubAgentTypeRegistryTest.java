package com.msz.resume.ai.agent;

import com.msz.resume.ai.chat.tooling.ArtifactTool;
import com.msz.resume.ai.chat.tooling.AskUserQuestionTool;
import com.msz.resume.ai.chat.tooling.MindmapTool;
import com.msz.resume.ai.chat.tooling.SpawnAgentTool;
import com.msz.resume.ai.chat.tooling.TaskPlanTool;
import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingMemoryService;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingUserMemoryService;
import com.msz.resume.ai.integrations.openviking.tooling.OpenVikingSearchTool;
import com.msz.resume.ai.integrations.openviking.tooling.OpenVikingSkillTool;
import com.msz.resume.ai.integrations.openviking.tooling.OpenVikingSkillWriteTool;
import com.msz.resume.ai.memory.tooling.ReadUserMemoryDetailTool;
import com.msz.resume.ai.memory.tooling.ReadUserMemoryTool;
import com.msz.resume.ai.memory.tooling.RememberUserMemoryTool;
import com.msz.resume.ai.memory.tooling.RememberUserPreferenceTool;
import com.msz.resume.ai.resume.tooling.ResumeGuideTool;
import com.msz.resume.ai.resume.tooling.ResumeOptimizeGuideTool;
import com.msz.resume.ai.tool.impl.GetCurrentTimeTool;
import com.msz.resume.ai.tool.impl.ToolSearchTool;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentTypeRegistryTest {

    @Test
    @DisplayName("ResumeBusinessExplore 应保留ReAct只读探索能力并排除发布写入类工具")
    void resumeBusinessExploreShouldExposeOnlyReadOnlyExplorationTools() {
        ToolRegistry registry = createRegistry();
        Set<String> toolNames = new SubAgentTypeRegistry()
                .getToolSpecifications(SubAgentType.ResumeBusinessExplore, registry)
                .stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("toolSearch"));
        assertTrue(toolNames.contains("getCurrentTime"));
        assertTrue(toolNames.contains("createPlan"));
        assertTrue(toolNames.contains("updateStatus"));
        assertTrue(toolNames.contains("addTask"));
        assertTrue(toolNames.contains("removeTask"));
        assertTrue(toolNames.contains("openviking_list"));
        assertTrue(toolNames.contains("openviking_tree"));
        assertTrue(toolNames.contains("openviking_read"));
        assertTrue(toolNames.contains("openviking_glob"));
        assertTrue(toolNames.contains("openviking_grep"));
        assertTrue(toolNames.contains("openviking_find"));
        assertTrue(toolNames.contains("openviking_search"));
        assertTrue(toolNames.contains("openviking_skill_read"));
        assertTrue(toolNames.contains("readUserMemory"));
        assertTrue(toolNames.contains("readUserMemoryDetail"));

        assertFalse(toolNames.contains("publishArtifact"));
        assertFalse(toolNames.contains("generateMindmap"));
        assertFalse(toolNames.contains("getResumeGuide"));
        assertFalse(toolNames.contains("getOptimizeGuide"));
        assertFalse(toolNames.contains("evaluateResume"));
        assertFalse(toolNames.contains("openviking_forget"));
        assertFalse(toolNames.contains("openviking_skill_add"));
        assertFalse(toolNames.contains("rememberUserMemory"));
        assertFalse(toolNames.contains("rememberUserPreference"));
        assertFalse(toolNames.contains("spawnAgent"));
        assertFalse(toolNames.contains("askUserQuestion"));
        assertFalse(toolNames.contains("askMultipleQuestions"));
        assertFalse(toolNames.contains("askQuestionnaire"));
    }

    @Test
    @DisplayName("Explore 只读类型不应因为核心工具默认注入拿到发布工具")
    void exploreShouldNotReceiveAllCoreTools() {
        ToolRegistry registry = createRegistry();
        Set<String> toolNames = new SubAgentTypeRegistry()
                .getToolSpecifications(SubAgentType.Explore, registry)
                .stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("toolSearch"));
        assertTrue(toolNames.contains("createPlan"));
        assertFalse(toolNames.contains("publishArtifact"));
        assertFalse(toolNames.contains("getResumeGuide"));
        assertFalse(toolNames.contains("openviking_forget"));
    }

    @Test
    @DisplayName("ResumeBusinessExplore 可追加 toolSearch 发现的读类延迟工具但不追加写类工具")
    void resumeBusinessExploreShouldAllowSafeDiscoveredReadToolsOnly() {
        ToolRegistry registry = createRegistry();
        registerDeferredTool(registry, "mcp_repo_read_resource");
        registerDeferredTool(registry, "mcp_repo_list_resources");
        registerDeferredTool(registry, "mcp_repo_write_resource");
        registerDeferredTool(registry, "mcp_repo_create_issue");

        Set<String> toolNames = new SubAgentTypeRegistry()
                .getToolSpecifications(
                        SubAgentType.ResumeBusinessExplore,
                        registry,
                        Set.of(
                                "mcp_repo_read_resource",
                                "mcp_repo_list_resources",
                                "mcp_repo_write_resource",
                                "mcp_repo_create_issue"
                        ))
                .stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("mcp_repo_read_resource"));
        assertTrue(toolNames.contains("mcp_repo_list_resources"));
        assertFalse(toolNames.contains("mcp_repo_write_resource"));
        assertFalse(toolNames.contains("mcp_repo_create_issue"));
    }

    private ToolRegistry createRegistry() {
        ToolRegistry registry = new ToolRegistry();
        OpenVikingProperties properties = new OpenVikingProperties();
        OpenVikingClient openVikingClient = new OpenVikingClient(properties);
        OpenVikingSkillService skillService = new OpenVikingSkillService(openVikingClient);
        OpenVikingUserMemoryService userMemoryService = new OpenVikingUserMemoryService(openVikingClient);

        registry.registerToolsFromObject(new GetCurrentTimeTool());
        registry.registerToolsFromObject(new ToolSearchTool(registry));
        registry.registerToolsFromObject(new AskUserQuestionTool());
        registry.registerToolsFromObject(new ArtifactTool());
        registry.registerToolsFromObject(new MindmapTool());
        registry.registerToolsFromObject(new TaskPlanTool());
        registry.registerToolsFromObject(new SpawnAgentTool());
        registry.registerToolsFromObject(new OpenVikingSearchTool(openVikingClient, properties));
        registry.registerToolsFromObject(new OpenVikingSkillTool(skillService));
        registry.registerToolsFromObject(new OpenVikingSkillWriteTool(skillService));
        registry.registerToolsFromObject(new ReadUserMemoryTool(userMemoryService));
        registry.registerToolsFromObject(new ReadUserMemoryDetailTool(userMemoryService));
        registry.registerToolsFromObject(new RememberUserMemoryTool(userMemoryService));
        registry.registerToolsFromObject(new RememberUserPreferenceTool(new OpenVikingMemoryService(openVikingClient)));
        registry.registerToolsFromObject(new ResumeGuideTool());
        registry.registerToolsFromObject(new ResumeOptimizeGuideTool());
        registerCoreTool(registry, "evaluateResume");

        return registry;
    }

    private void registerDeferredTool(ToolRegistry registry, String name) {
        registerTool(registry, name, ToolRegistry.ToolExposure.DEFERRED);
    }

    private void registerCoreTool(ToolRegistry registry, String name) {
        registerTool(registry, name, ToolRegistry.ToolExposure.CORE);
    }

    private void registerTool(ToolRegistry registry, String name, ToolRegistry.ToolExposure exposure) {
        ToolSpecification spec = ToolSpecification.builder()
                .name(name)
                .description(name)
                .parameters(JsonObjectSchema.builder()
                        .additionalProperties(false)
                        .build())
                .build();
        ToolExecutor executor = (request, memoryId) -> "{}";
        registry.registerTool(name, spec, executor, exposure, executor);
    }
}
