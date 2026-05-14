package com.msz.resume.ai.chat.prompt.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YamlPromptConfigLoader 集成测试
 *
 * <p>使用 Spring Boot Test 加载配置文件进行测试
 */
@SpringBootTest
@DisplayName("YamlPromptConfigLoader 测试")
class YamlPromptConfigLoaderTest {

    @Autowired
    private PromptConfigLoader configLoader;

    @Test
    @DisplayName("应加载 intro section 模板")
    void testLoadSectionTemplate() {
        String template = configLoader.loadSectionTemplate("intro");

        assertNotNull(template);
        assertFalse(template.isEmpty());
        assertTrue(template.contains("JARVIS") || template.contains("assistant") || template.length() > 10,
                "intro section 应包含内容");
    }

    @Test
    @DisplayName("禁用的 section 应返回空字符串")
    void testLoadSectionTemplateDisabled() {
        // memory section 在配置中 enabled: false
        String template = configLoader.loadSectionTemplate("memory");

        assertEquals("", template);
    }

    @Test
    @DisplayName("不存在的 section 应返回空字符串")
    void testLoadSectionTemplateNotFound() {
        String template = configLoader.loadSectionTemplate("non_existent_section");

        assertEquals("", template);
    }

    @Test
    @DisplayName("应正确检查 section 开关状态")
    void testIsSectionEnabled() {
        assertTrue(configLoader.isSectionEnabled("intro"));
        assertTrue(configLoader.isSectionEnabled("tone_and_style"));
        assertTrue(configLoader.isSectionEnabled("output_efficiency"));
        assertTrue(configLoader.isSectionEnabled("using_your_tools"));

        // memory section 是禁用的
        assertFalse(configLoader.isSectionEnabled("memory"));
    }

    @Test
    @DisplayName("未知 section 默认应启用")
    void testIsSectionEnabledDefault() {
        // 配置文件中没有明确禁用的 section 默认启用
        assertTrue(configLoader.isSectionEnabled("unknown_section"));
    }

    @Test
    @DisplayName("应获取全局配置")
    void testGetConfig() {
        // model_name 已移除，改为从 LLMConfig 动态读取
        Optional<String> modelName = configLoader.getConfig("model_name");
        assertFalse(modelName.isPresent(), "model_name 应该已从配置中移除");

        Optional<String> knowledgeCutoff = configLoader.getConfig("knowledge_cutoff");
        assertTrue(knowledgeCutoff.isPresent());
        assertEquals("2026-04", knowledgeCutoff.get());
    }

    @Test
    @DisplayName("不存在的配置应返回空")
    void testGetConfigNotFound() {
        Optional<String> config = configLoader.getConfig("non_existent_config");

        assertFalse(config.isPresent());
    }

    @Test
    @DisplayName("应加载所有已启用的 section 模板")
    void testLoadAllSectionTemplates() {
        Map<String, String> templates = configLoader.loadAllSectionTemplates();

        assertNotNull(templates);
        assertFalse(templates.isEmpty());

        // 静态 section 应存在
        assertTrue(templates.containsKey("intro"));
        assertTrue(templates.containsKey("tone_and_style"));
        assertTrue(templates.containsKey("output_efficiency"));
        assertTrue(templates.containsKey("using_your_tools"));

        // 禁用的 memory 不应存在
        assertFalse(templates.containsKey("memory"));
    }

    @Test
    @DisplayName("热更新应重新加载配置")
    void testReload() {
        // 获取当前状态
        String introBefore = configLoader.loadSectionTemplate("intro");
        Map<String, String> templatesBefore = configLoader.loadAllSectionTemplates();

        // 执行热更新
        configLoader.reload();

        // 验证重新加载后数据一致
        String introAfter = configLoader.loadSectionTemplate("intro");
        Map<String, String> templatesAfter = configLoader.loadAllSectionTemplates();

        assertEquals(introBefore, introAfter);
        assertEquals(templatesBefore.size(), templatesAfter.size());
    }

    @Test
    @DisplayName("section 模板内容应非空")
    void testSectionTemplateContent() {
        String[] staticSections = {"intro", "tone_and_style", "output_efficiency", "using_your_tools"};

        for (String section : staticSections) {
            String template = configLoader.loadSectionTemplate(section);
            assertNotNull(template, section + " 模板不应为 null");
            assertFalse(template.isEmpty(), section + " 模板不应为空");
        }
    }

    @Test
    @DisplayName("工具指南应包含任务规划规则")
    void testUsingYourToolsContainsTaskPlanningRules() {
        String template = configLoader.loadSectionTemplate("using_your_tools");

        assertTrue(template.contains("### 任务规划"));
        assertTrue(template.contains("createPlan"));
        assertTrue(template.contains("updateStatus"));
        assertTrue(template.contains("上下文压缩"));
    }
}
