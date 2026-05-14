package com.msz.resume.ai.chat.prompt.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SectionName 单元测试
 */
@DisplayName("SectionName 测试")
class SectionNameTest {

    @Test
    @DisplayName("staticSections() 应返回4个静态section")
    void testStaticSections() {
        Set<String> staticSections = SectionName.staticSections();

        assertEquals(4, staticSections.size());
        assertTrue(staticSections.contains(SectionName.INTRO));
        assertTrue(staticSections.contains(SectionName.TONE_AND_STYLE));
        assertTrue(staticSections.contains(SectionName.OUTPUT_EFFICIENCY));
        assertTrue(staticSections.contains(SectionName.USING_YOUR_TOOLS));
    }

    @Test
    @DisplayName("dynamicSections() 应返回6个动态section")
    void testDynamicSections() {
        Set<String> dynamicSections = SectionName.dynamicSections();

        assertEquals(6, dynamicSections.size());
        assertTrue(dynamicSections.contains(SectionName.SESSION_GUIDANCE));
        assertTrue(dynamicSections.contains(SectionName.ENV_INFO));
        assertTrue(dynamicSections.contains(SectionName.USER_CONTEXT));
        assertTrue(dynamicSections.contains(SectionName.USER_PREFERENCES));
        assertTrue(dynamicSections.contains(SectionName.MEMORY));
        assertTrue(dynamicSections.contains(SectionName.SUB_AGENT_CONTEXT));
    }

    @Test
    @DisplayName("静态section常量值应正确")
    void testStaticSectionConstants() {
        assertEquals("intro", SectionName.INTRO);
        assertEquals("tone_and_style", SectionName.TONE_AND_STYLE);
        assertEquals("output_efficiency", SectionName.OUTPUT_EFFICIENCY);
        assertEquals("using_your_tools", SectionName.USING_YOUR_TOOLS);
    }

    @Test
    @DisplayName("动态section常量值应正确")
    void testDynamicSectionConstants() {
        assertEquals("session_guidance", SectionName.SESSION_GUIDANCE);
        assertEquals("env_info", SectionName.ENV_INFO);
        assertEquals("user_context", SectionName.USER_CONTEXT);
        assertEquals("user_preferences", SectionName.USER_PREFERENCES);
        assertEquals("memory", SectionName.MEMORY);
        assertEquals("sub_agent_context", SectionName.SUB_AGENT_CONTEXT);
    }

    @Test
    @DisplayName("静态和动态section应无交集")
    void testNoOverlap() {
        Set<String> staticSections = SectionName.staticSections();
        Set<String> dynamicSections = SectionName.dynamicSections();

        for (String section : staticSections) {
            assertFalse(dynamicSections.contains(section),
                    "静态section不应出现在动态section中: " + section);
        }

        for (String section : dynamicSections) {
            assertFalse(staticSections.contains(section),
                    "动态section不应出现在静态section中: " + section);
        }
    }

    @Test
    @DisplayName("返回的Set应不可变")
    void testImmutableSets() {
        Set<String> staticSections = SectionName.staticSections();
        Set<String> dynamicSections = SectionName.dynamicSections();

        assertThrows(UnsupportedOperationException.class, () ->
                staticSections.add("new_section"));

        assertThrows(UnsupportedOperationException.class, () ->
                dynamicSections.add("new_section"));
    }
}
