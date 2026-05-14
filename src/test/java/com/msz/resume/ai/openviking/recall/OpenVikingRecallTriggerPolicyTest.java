package com.msz.resume.ai.integrations.openviking.core.recall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenVikingRecallTriggerPolicyTest {

    private final OpenVikingRecallTriggerPolicy policy = new OpenVikingRecallTriggerPolicy();

    @Test
    @DisplayName("知识库架构类问题应触发 resource 召回")
    void shouldTriggerResourceRecallForArchitectureQuestion() {
        OpenVikingRecallResult result = policy.evaluate(
                "讲一下 Agent Loop 架构升级了什么",
                new OpenVikingRecallProperties()
        );

        assertTrue(result.shouldRecall());
        assertEquals("triggered", result.status());
        assertTrue(result.targetScopes().contains("resource"));
        assertTrue(result.triggerReasons().contains("resource_keyword"));
    }

    @Test
    @DisplayName("用户明确要求不要查知识库且直接按理解说时应跳过召回")
    void shouldSkipWhenUserRequestsNoRecall() {
        OpenVikingRecallResult result = policy.evaluate(
                "不要查知识库，直接按你的理解说",
                new OpenVikingRecallProperties()
        );

        assertFalse(result.shouldRecall());
        assertEquals("skipped", result.status());
        assertEquals("user_requested_no_recall", result.reason());
    }

    @Test
    @DisplayName("不要查知识库只应禁用 resource，不应阻止 memory 召回")
    void shouldOnlySuppressResourceWhenUserRequestsNoKnowledgeBase() {
        OpenVikingRecallResult result = policy.evaluate(
                "不要查知识库，但记忆里我之前说过输出风格偏好吗",
                new OpenVikingRecallProperties()
        );

        assertTrue(result.shouldRecall());
        assertFalse(result.targetScopes().contains("resource"));
        assertTrue(result.targetScopes().contains("memory"));
    }

    @Test
    @DisplayName("简单问候应跳过召回")
    void shouldSkipSimpleGreeting() {
        OpenVikingRecallResult result = policy.evaluate("你好", new OpenVikingRecallProperties());

        assertFalse(result.shouldRecall());
        assertEquals("simple_greeting", result.reason());
    }

    @Test
    @DisplayName("记忆类问题应触发 memory 召回")
    void shouldTriggerMemoryRecallForPreferenceQuestion() {
        OpenVikingRecallResult result = policy.evaluate(
                "我之前说过输出风格偏好吗",
                new OpenVikingRecallProperties()
        );

        assertTrue(result.shouldRecall());
        assertTrue(result.targetScopes().contains("memory"));
    }

    @Test
    @DisplayName("skill 类问题应触发 skill 召回")
    void shouldTriggerSkillRecallForSkillQuestion() {
        OpenVikingRecallResult result = policy.evaluate(
                "有没有适合做架构分析的 skill",
                new OpenVikingRecallProperties()
        );

        assertTrue(result.shouldRecall());
        assertTrue(result.targetScopes().contains("skill"));
    }

    @Test
    @DisplayName("关闭 resource scope 后架构关键词不应触发 resource")
    void shouldRespectDisabledResourceScope() {
        OpenVikingRecallProperties properties = new OpenVikingRecallProperties();
        properties.setResourceScopeEnabled(false);

        OpenVikingRecallResult result = policy.evaluate("讲一下 Agent Loop 架构升级了什么", properties);

        assertFalse(result.targetScopes().contains("resource"));
    }
}
