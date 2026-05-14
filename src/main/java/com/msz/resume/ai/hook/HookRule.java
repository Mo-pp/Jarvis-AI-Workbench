package com.msz.resume.ai.hook;

/**
 * 单条 Hook 规则
 *
 * <p>对应 YAML 中的一条规则配置，包含工具名匹配模式、Hook 实现名称、优先级。
 *
 * <p>规则加载流程：
 * <pre>
 * tool-hooks.yml → HookConfigProperties 解析 → HookRule 列表 → HookEngine 匹配执行
 * </pre>
 */
public record HookRule(

        /** 规则名称（唯一标识，用于日志追踪） */
        String name,

        /** 工具名正则匹配模式，如 "askUserQuestion|askMultipleQuestions" */
        String matcher,

        /** Hook 实现的 Spring Bean 名称，如 "askUserQuestionHook" */
        String action,

        /** 执行优先级，数字越小越先执行（默认 100） */
        int priority
) {}