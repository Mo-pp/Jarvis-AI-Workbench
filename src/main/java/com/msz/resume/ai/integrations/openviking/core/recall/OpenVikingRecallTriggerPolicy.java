package com.msz.resume.ai.integrations.openviking.core.recall;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic Phase 1 policy for deciding whether an OV recall pass should run.
 */
@Component
public class OpenVikingRecallTriggerPolicy {

    private static final Pattern SIMPLE_GREETING = Pattern.compile(
            "^(hi|hello|hey|你好|您好|嗨|在吗|早上好|晚上好|下午好)[!！。,.，\\s]*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SIMPLE_ARITHMETIC = Pattern.compile("^\\s*\\d+\\s*[+\\-*/×÷]\\s*\\d+\\s*(等于多少|是多少|=|\\?)?\\s*$");

    private static final List<String> IGNORE_ALL_CONTEXT_KEYWORDS = List.of(
            "直接按你的理解", "不使用 ov", "不用 ov", "不要用 ov",
            "without ov", "do not use ov", "ignore ov"
    );

    private static final List<String> IGNORE_RESOURCE_KEYWORDS = List.of(
            "不要查知识库", "别查知识库", "不用查知识库", "不要用知识库", "忽略知识库",
            "do not search knowledge", "without knowledge base"
    );

    private static final List<String> IGNORE_MEMORY_KEYWORDS = List.of(
            "不要查记忆", "别查记忆", "不用查记忆", "不要用记忆", "忽略记忆",
            "do not use memory", "ignore memory"
    );

    private static final List<String> RESOURCE_KEYWORDS = List.of(
            "文档", "知识库", "资料", "方案", "架构", "设计", "升级", "规范", "prd",
            "总结", "讲一下", "说明", "解释", "对比", "实现计划", "计划", "接口设计"
    );

    private static final List<String> MEMORY_KEYWORDS = List.of(
            "之前", "记得", "我说过", "我的偏好", "长期记忆", "记忆里", "上次",
            "preference", "memory", "remember"
    );

    private static final List<String> SKILL_KEYWORDS = List.of(
            "skill", "技能", "能力", "流程", "怎么做这类任务", "适合做", "有没有适合", "按某流程"
    );

    public OpenVikingRecallResult evaluate(String latestUserText, OpenVikingRecallProperties properties) {
        String query = latestUserText != null ? latestUserText.trim() : "";
        if (query.isBlank()) {
            return OpenVikingRecallResult.skipped("no_user_query", query);
        }
        String normalized = query.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, IGNORE_ALL_CONTEXT_KEYWORDS)) {
            return OpenVikingRecallResult.skipped("user_requested_no_recall", query);
        }
        if (SIMPLE_GREETING.matcher(query).matches()) {
            return OpenVikingRecallResult.skipped("simple_greeting", query);
        }
        if (SIMPLE_ARITHMETIC.matcher(query).matches()) {
            return OpenVikingRecallResult.skipped("simple_arithmetic", query);
        }

        List<String> reasons = new ArrayList<>();
        Set<String> scopes = new LinkedHashSet<>();
        boolean resourceAllowed = properties.isResourceScopeEnabled() && !containsAny(normalized, IGNORE_RESOURCE_KEYWORDS);
        boolean memoryAllowed = properties.isMemoryScopeEnabled() && !containsAny(normalized, IGNORE_MEMORY_KEYWORDS);

        if (resourceAllowed && containsAny(normalized, RESOURCE_KEYWORDS)) {
            reasons.add("resource_keyword");
            scopes.add("resource");
        }
        if (memoryAllowed && containsAny(normalized, MEMORY_KEYWORDS)) {
            reasons.add("memory_keyword");
            scopes.add("memory");
        }
        if (properties.isSkillScopeEnabled() && containsAny(normalized, SKILL_KEYWORDS)) {
            reasons.add("skill_keyword");
            scopes.add("skill");
        }
        if (resourceAllowed && looksLikeNamedConcept(query)) {
            reasons.add("named_concept");
            scopes.add("resource");
        }

        if (scopes.isEmpty()) {
            return OpenVikingRecallResult.skipped("no_trigger_match", query);
        }
        return OpenVikingRecallResult.triggered(query, reasons, new ArrayList<>(scopes));
    }

    private boolean containsAny(String normalized, List<String> keywords) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeNamedConcept(String query) {
        if (query.length() < 8) {
            return false;
        }
        int asciiUppercaseWords = 0;
        for (String token : query.split("\\s+")) {
            if (token.length() >= 2 && Character.isUpperCase(token.charAt(0))) {
                asciiUppercaseWords++;
            }
        }
        return asciiUppercaseWords >= 2;
    }
}
