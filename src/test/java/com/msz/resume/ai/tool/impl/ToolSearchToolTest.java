package com.msz.resume.ai.tool.impl;

import com.msz.resume.ai.tool.CoreTool;
import com.msz.resume.ai.tool.registry.ToolHint;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolSearchTool 单元测试
 */
class ToolSearchToolTest {

    private ToolRegistry toolRegistry;
    private ToolSearchTool toolSearchTool;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolSearchTool = new ToolSearchTool(toolRegistry);

        // 注册测试工具
        toolRegistry.registerToolsFromObject(new DeferredTestTool());
        toolRegistry.registerToolsFromObject(new AnotherDeferredTool());
        toolRegistry.registerToolsFromObject(new CoreTestTool());
    }

    @Test
    @DisplayName("精确匹配工具名应返回完整 schema")
    void testExactMatchReturnsFullSchema() {
        String result = toolSearchTool.toolSearch("deferredMethod");

        assertNotNull(result);
        assertTrue(result.contains("\"name\""));
        assertTrue(result.contains("deferredMethod"));
        assertTrue(result.contains("\"description\""));
        assertTrue(result.contains("\"parameters\""));
    }

    @Test
    @DisplayName("模糊匹配应返回匹配的工具列表")
    void testFuzzyMatchReturnsToolList() {
        String result = toolSearchTool.toolSearch("deferred");

        assertNotNull(result);
        assertTrue(result.contains("匹配"));
        assertTrue(result.contains("deferredMethod") || result.contains("anotherMethod"));
    }

    @Test
    @DisplayName("无匹配应返回可用工具列表")
    void testNoMatchReturnsAvailableTools() {
        String result = toolSearchTool.toolSearch("nonexistent");

        assertNotNull(result);
        assertTrue(result.contains("未找到"));
        assertTrue(result.contains("可用的延迟工具"));
    }

    @Test
    @DisplayName("空查询应返回所有延迟工具")
    void testEmptyQueryReturnsAllDeferredTools() {
        String result = toolSearchTool.toolSearch("");

        assertNotNull(result);
        assertTrue(result.contains("可用的延迟工具"));
    }

    @Test
    @DisplayName("null 查询应返回所有延迟工具")
    void testNullQueryReturnsAllDeferredTools() {
        String result = toolSearchTool.toolSearch(null);

        assertNotNull(result);
        assertTrue(result.contains("可用的延迟工具"));
    }

    @Test
    @DisplayName("不应搜索核心工具")
    void testShouldNotSearchCoreTools() {
        String result = toolSearchTool.toolSearch("coreMethod");

        // coreMethod 是核心工具，不应被搜索到
        assertTrue(result.contains("未找到") || !result.contains("coreMethod"));
    }

    @Test
    @DisplayName("搜索描述中的关键词应返回匹配工具")
    void testSearchByDescription() {
        String result = toolSearchTool.toolSearch("测试");

        assertNotNull(result);
        // 应该匹配到包含"测试"的工具描述
    }

    @Test
    @DisplayName("getDeferredToolHints 应只返回延迟工具")
    void testGetDeferredToolHintsOnlyReturnsDeferredTools() {
        List<ToolHint> hints = toolRegistry.getDeferredToolHints();

        assertNotNull(hints);
        // 只包含延迟工具，不包含核心工具
        assertTrue(hints.stream().anyMatch(h -> h.name().equals("deferredMethod") || h.name().equals("anotherMethod")));
        assertFalse(hints.stream().anyMatch(h -> h.name().equals("coreMethod")));
    }

    @Test
    @DisplayName("searchDeferredTools 应正确模糊匹配")
    void testSearchDeferredToolsFuzzyMatch() {
        List<ToolHint> results = toolRegistry.searchDeferredTools("another");

        assertEquals(1, results.size());
        assertEquals("anotherMethod", results.get(0).name());
    }

    // ==================== 测试工具类 ====================

    /**
     * 延迟测试工具
     */
    static class DeferredTestTool {
        @Tool("延迟测试工具描述")
        public String deferredMethod(String input) {
            return "deferred: " + input;
        }
    }

    /**
     * 另一个延迟测试工具
     */
    static class AnotherDeferredTool {
        @Tool("另一个延迟工具")
        public String anotherMethod() {
            return "another";
        }
    }

    /**
     * 核心测试工具
     */
    @CoreTool
    static class CoreTestTool {
        @Tool("核心测试工具")
        public String coreMethod() {
            return "core";
        }
    }
}
