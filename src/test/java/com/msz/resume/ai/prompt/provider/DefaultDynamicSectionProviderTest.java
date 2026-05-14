package com.msz.resume.ai.chat.prompt.provider;

import com.msz.resume.ai.chat.llm.config.LLMConfig;
import com.msz.resume.ai.chat.prompt.config.PromptConfigLoader;
import com.msz.resume.ai.tool.CoreTool;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultDynamicSectionProvider session_guidance 格式测试
 *
 * 验证核心工具和延迟工具的分开展示
 */
class DefaultDynamicSectionProviderTest {

    private ToolRegistry toolRegistry;
    private DefaultDynamicSectionProvider provider;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();

        // 创建简单的 mock PromptConfigLoader
        PromptConfigLoader mockLoader = new PromptConfigLoader() {
            @Override
            public String loadSectionTemplate(String sectionName) {
                return "";
            }

            @Override
            public boolean isSectionEnabled(String sectionName) {
                return true;
            }

            @Override
            public java.util.Map<String, String> loadAllSectionTemplates() {
                return java.util.Map.of();
            }

            @Override
            public void reload() {}

            @Override
            public java.util.Optional<String> getConfig(String configKey) {
                return java.util.Optional.empty();
            }

            @Override
            public String getModelName() {
                return "test-model";
            }

            @Override
            public String getKnowledgeCutoff() {
                return "2026-01";
            }
        };

        // 创建 mock LLMConfig
        LLMConfig mockLlmConfig = new LLMConfig();
        mockLlmConfig.setProvider("zhipu");
        mockLlmConfig.getZhipu().setModel("glm-4.7");

        // OpenVikingMemoryService 和 OpenVikingUserMemoryService 不被 getSessionGuidance 使用，
        // 可以安全传入 null（仅测试 session_guidance 相关方法）
        provider = new DefaultDynamicSectionProvider(mockLoader, mockLlmConfig, null, null);
    }

    @Test
    @DisplayName("空工具注册应返回空字符串")
    void testEmptyRegistryReturnsEmpty() {
        String result = provider.getSessionGuidance(toolRegistry);
        assertEquals("", result);
    }

    @Test
    @DisplayName("只有核心工具时应显示完整描述")
    void testOnlyCoreToolsShowsFullDescription() {
        toolRegistry.registerToolsFromObject(new CoreTestTool());

        String result = provider.getSessionGuidance(toolRegistry);

        assertTrue(result.contains("## Session Guidance"));
        assertTrue(result.contains("You have direct access to the following tools:"));
        assertTrue(result.contains("coreMethod"));
        assertTrue(result.contains("核心测试工具描述"));
        assertFalse(result.contains("Additional tools"));
    }

    @Test
    @DisplayName("只有延迟工具时应只显示名称")
    void testOnlyDeferredToolsShowsNamesOnly() {
        toolRegistry.registerToolsFromObject(new DeferredTestTool());

        String result = provider.getSessionGuidance(toolRegistry);

        assertTrue(result.contains("## Session Guidance"));
        assertTrue(result.contains("Additional tools (use toolSearch to learn details):"));
        assertTrue(result.contains("deferredMethod"));
        // 延迟工具不应显示描述
        assertFalse(result.contains("延迟测试工具描述"));
    }

    @Test
    @DisplayName("核心和延迟工具都存在时应分开展示")
    void testBothToolsShowsSeparately() {
        toolRegistry.registerToolsFromObject(new CoreTestTool());
        toolRegistry.registerToolsFromObject(new DeferredTestTool());

        String result = provider.getSessionGuidance(toolRegistry);

        // 核心工具部分
        assertTrue(result.contains("You have direct access to the following tools:"));
        assertTrue(result.contains("coreMethod"));
        assertTrue(result.contains("核心测试工具描述"));

        // 延迟工具部分
        assertTrue(result.contains("Additional tools (use toolSearch to learn details):"));
        assertTrue(result.contains("deferredMethod"));

        // 验证延迟工具只显示名称，不显示描述
        int deferredIndex = result.indexOf("Additional tools");
        String deferredSection = result.substring(deferredIndex);
        assertFalse(deferredSection.contains("延迟测试工具描述"));
    }

    @Test
    @DisplayName("多个核心工具应都显示完整描述")
    void testMultipleCoreToolsShowsAll() {
        toolRegistry.registerToolsFromObject(new CoreTestTool());
        toolRegistry.registerToolsFromObject(new AnotherCoreTool());

        String result = provider.getSessionGuidance(toolRegistry);

        assertTrue(result.contains("coreMethod"));
        assertTrue(result.contains("anotherCore"));
        assertTrue(result.contains("核心测试工具描述"));
        assertTrue(result.contains("另一个核心工具"));
    }

    @Test
    @DisplayName("多个延迟工具应只显示名称列表")
    void testMultipleDeferredToolsShowsNamesList() {
        toolRegistry.registerToolsFromObject(new DeferredTestTool());
        toolRegistry.registerToolsFromObject(new AnotherDeferredTool());

        String result = provider.getSessionGuidance(toolRegistry);

        assertTrue(result.contains("deferredMethod"));
        assertTrue(result.contains("anotherDeferred"));
        // 都不应显示描述
        int additionalIndex = result.indexOf("Additional tools");
        String additionalSection = result.substring(additionalIndex);
        assertFalse(additionalSection.contains("延迟测试工具描述"));
        assertFalse(additionalSection.contains("另一个延迟工具"));
    }

    // ==================== 测试工具类 ====================

    @CoreTool
    static class CoreTestTool {
        @Tool("核心测试工具描述")
        public String coreMethod() {
            return "core";
        }
    }

    @CoreTool
    static class AnotherCoreTool {
        @Tool("另一个核心工具")
        public String anotherCore() {
            return "core2";
        }
    }

    static class DeferredTestTool {
        @Tool("延迟测试工具描述")
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
