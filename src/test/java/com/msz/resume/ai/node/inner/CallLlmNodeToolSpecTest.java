package com.msz.resume.ai.chat.runtime.node.inner;

import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.tool.CoreTool;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具规格合并逻辑测试
 *
 * 验证核心工具 + 已发现延迟工具的合并逻辑
 */
class CallLlmNodeToolSpecTest {

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.registerToolsFromObject(new CoreTestTool());
        toolRegistry.registerToolsFromObject(new DeferredTestTool());
        toolRegistry.registerToolsFromObject(new AnotherDeferredTool());
    }

    @Test
    @DisplayName("getCoreToolSpecifications 应只返回核心工具")
    void testGetCoreToolSpecifications() {
        List<ToolSpecification> specs = toolRegistry.getCoreToolSpecifications();

        assertEquals(1, specs.size());
        assertEquals("coreMethod", specs.get(0).name());
    }

    @Test
    @DisplayName("getDeferredToolSpecifications 应返回延迟工具")
    void testGetDeferredToolSpecifications() {
        Set<String> names = new LinkedHashSet<>();
        names.add("deferredMethod");
        names.add("anotherDeferred");

        List<ToolSpecification> specs = toolRegistry.getDeferredToolSpecifications(names);

        assertEquals(2, specs.size());
        Set<String> specNames = new HashSet<>();
        specs.forEach(s -> specNames.add(s.name()));
        assertTrue(specNames.contains("deferredMethod"));
        assertTrue(specNames.contains("anotherDeferred"));
    }

    @Test
    @DisplayName("合并核心工具和已发现工具应正确")
    void testMergeCoreAndDiscovered() {
        // 模拟 CallLlmNode.buildToolSpecifications 的逻辑
        List<ToolSpecification> specs = new ArrayList<>(toolRegistry.getCoreToolSpecifications());

        Set<String> discoveredNames = new LinkedHashSet<>();
        discoveredNames.add("deferredMethod");
        discoveredNames.add("anotherDeferred");

        List<ToolSpecification> discoveredSpecs = toolRegistry.getDeferredToolSpecifications(discoveredNames);
        specs.addAll(discoveredSpecs);

        // 去重
        Set<String> seen = new HashSet<>();
        List<ToolSpecification> result = specs.stream()
                .filter(spec -> seen.add(spec.name()))
                .toList();

        // 验证结果
        assertEquals(3, result.size());

        Set<String> resultNames = new HashSet<>();
        result.forEach(s -> resultNames.add(s.name()));
        assertTrue(resultNames.contains("coreMethod"));
        assertTrue(resultNames.contains("deferredMethod"));
        assertTrue(resultNames.contains("anotherDeferred"));
    }

    @Test
    @DisplayName("空已发现集合应只返回核心工具")
    void testEmptyDiscovered() {
        List<ToolSpecification> specs = new ArrayList<>(toolRegistry.getCoreToolSpecifications());

        Set<String> discoveredNames = new LinkedHashSet<>();
        if (discoveredNames != null && !discoveredNames.isEmpty()) {
            specs.addAll(toolRegistry.getDeferredToolSpecifications(discoveredNames));
        }

        assertEquals(1, specs.size());
        assertEquals("coreMethod", specs.get(0).name());
    }

    @Test
    @DisplayName("QueryLoopState DISCOVERED_TOOLS 默认为空集合")
    void testDiscoveredToolsDefaultEmpty() {
        Map<String, Object> initData = new HashMap<>();
        initData.put(QueryLoopState.MESSAGE_HISTORY, new ArrayList<>());
        QueryLoopState state = new QueryLoopState(initData);

        Set<String> discovered = state.getDiscoveredTools();
        assertNotNull(discovered);
        assertTrue(discovered.isEmpty());
    }

    @Test
    @DisplayName("QueryLoopState DISCOVERED_TOOLS 可正确存取")
    void testDiscoveredToolsAccess() {
        Map<String, Object> initData = new HashMap<>();
        initData.put(QueryLoopState.MESSAGE_HISTORY, new ArrayList<>());
        Set<String> discovered = new LinkedHashSet<>();
        discovered.add("tool1");
        discovered.add("tool2");
        initData.put(QueryLoopState.DISCOVERED_TOOLS, discovered);

        QueryLoopState state = new QueryLoopState(initData);

        Set<String> result = state.getDiscoveredTools();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("tool1"));
        assertTrue(result.contains("tool2"));
    }

    // ==================== 测试工具类 ====================

    @CoreTool
    static class CoreTestTool {
        @Tool("核心测试工具")
        public String coreMethod() {
            return "core";
        }
    }

    static class DeferredTestTool {
        @Tool("延迟测试工具")
        public String deferredMethod(String input) {
            return "deferred: " + input;
        }
    }

    static class AnotherDeferredTool {
        @Tool("另一个延迟工具")
        public String anotherDeferred() {
            return "another";
        }
    }
}
