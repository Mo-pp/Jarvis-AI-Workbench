package com.msz.resume.ai.chat.prompt.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptResult 单元测试
 */
@DisplayName("PromptResult 测试")
class PromptResultTest {

    @Test
    @DisplayName("empty() 应返回全空结果")
    void testEmpty() {
        PromptResult result = PromptResult.empty();

        assertEquals("", result.systemPrompt());
        assertEquals("", result.staticSectionContent());
        assertEquals("", result.dynamicSectionContent());
        assertEquals(0, result.tokenEstimate());
    }

    @Test
    @DisplayName("BOUNDARY 常量应为正确值")
    void testBoundaryConstant() {
        assertEquals("__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__", PromptResult.BOUNDARY);
    }

    @Test
    @DisplayName("record 构造和 getter 应正常工作")
    void testRecordCreation() {
        PromptResult result = new PromptResult(
                "full prompt",
                "static part",
                "dynamic part",
                100
        );

        assertEquals("full prompt", result.systemPrompt());
        assertEquals("static part", result.staticSectionContent());
        assertEquals("dynamic part", result.dynamicSectionContent());
        assertEquals(100, result.tokenEstimate());
    }

    @Test
    @DisplayName("tokenEstimate 为零时应正常记录")
    void testZeroTokenEstimate() {
        PromptResult result = new PromptResult("test", "static", "dynamic", 0);

        assertEquals(0, result.tokenEstimate());
    }

    @Test
    @DisplayName("各字段可为 null")
    void testNullableFields() {
        PromptResult result = new PromptResult(null, null, null, 0);

        assertNull(result.systemPrompt());
        assertNull(result.staticSectionContent());
        assertNull(result.dynamicSectionContent());
    }
}
