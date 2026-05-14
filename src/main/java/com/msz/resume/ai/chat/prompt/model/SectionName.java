package com.msz.resume.ai.chat.prompt.model;

import java.util.Set;

/**
 * Section名称常量
 *
 * <p>定义所有静态和动态section的名称常量。
 */
public final class SectionName {

    // 静态Section（可缓存，内容固定）
    public static final String INTRO = "intro";
    public static final String TONE_AND_STYLE = "tone_and_style";
    public static final String OUTPUT_EFFICIENCY = "output_efficiency";
    public static final String USING_YOUR_TOOLS = "using_your_tools";

    // 动态Section（每轮重新计算，内容随请求变化）
    public static final String SESSION_GUIDANCE = "session_guidance";
    public static final String ENV_INFO = "env_info";
    public static final String USER_CONTEXT = "user_context";
    public static final String USER_PREFERENCES = "user_preferences";
    public static final String MEMORY = "memory";
    public static final String SUB_AGENT_CONTEXT = "sub_agent_context";

    private SectionName() {
        // 工具类，禁止实例化
    }

    /**
     * 获取所有静态section名称
     *
     * <p>静态section的内容固定，可以缓存复用（对前缀缓存友好）。
     * 子Agent复用相同的静态前缀，确保缓存命中。
     */
    public static Set<String> staticSections() {
        return Set.of(INTRO, TONE_AND_STYLE, OUTPUT_EFFICIENCY, USING_YOUR_TOOLS);
    }

    /**
     * 获取所有动态section名称
     *
     * <p>动态section每轮请求都可能不同，需要实时生成。
     * sub_agent_context 包含运行时任务描述，属于动态section。
     */
    public static Set<String> dynamicSections() {
        return Set.of(SESSION_GUIDANCE, ENV_INFO, USER_CONTEXT, USER_PREFERENCES, MEMORY, SUB_AGENT_CONTEXT);
    }
}
