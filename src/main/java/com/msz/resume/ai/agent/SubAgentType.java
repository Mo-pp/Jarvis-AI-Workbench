package com.msz.resume.ai.agent;

import java.util.Set;
import java.util.Locale;

/**
 * 预定义子Agent类型
 *
 * <p>类似Claude Code的Agent架构，每种类型有固定的工具白名单和黑名单配置。
 * 主Agent只需选择"调用哪种类型的Agent"，不需要手动指定工具列表。
 *
 * <h2>类型说明</h2>
 * <ul>
 *   <li><b>General</b>: 通用Agent，可使用所有工具，支持读写操作</li>
 *   <li><b>Plan</b>: 规划Agent，只读模式，用于探索代码库、设计实现方案</li>
 *   <li><b>Explore</b>: 探索Agent，只读模式，用于快速搜索代码、回答问题</li>
 *   <li><b>ResumeBusinessExplore</b>: 简历项目业务探索Agent，只读模式，用于从项目证据提炼业务链路、优化故事和候选简历表达</li>
 * </ul>
 *
 * <h2>工具过滤机制</h2>
 * <p>两层过滤：
 * <ol>
 *   <li>全局黑名单（{@link #GLOBAL_EXCLUDED_TOOLS}）</li>
 *   <li>Per-Agent白名单/黑名单（本枚举定义）</li>
 * </ol>
 *
 * @see SubAgentTypeRegistry
 */
public enum SubAgentType {

    /**
     * 通用Agent - 可使用所有工具（读写模式）
     *
     * <p>用于复杂多步任务、代码修改、文件操作等。
     * 可通过 allowedTools 参数自定义工具集。
     */
    General(
        "通用Agent，用于复杂多步任务、代码修改、文件操作等",
        Set.of("*"),  // 白名单：所有工具
        Set.of()      // 黑名单：空（由全局黑名单处理）
    ),

    /**
     * 规划Agent - 只读模式，用于设计实现方案
     *
     * <p>用于探索代码库、理解架构、设计实现方案。
     * 不能修改文件或执行写操作。
     * allowedTools 参数会被忽略。
     * 支持任务规划工具，可分解复杂任务。
     */
    Plan(
        "规划Agent，用于探索代码库、设计实现方案。只读模式，不能修改文件。支持任务规划。",
        Set.of("toolSearch", "getCurrentTime", "grep", "glob", "read",
                "createPlan", "updateStatus", "addTask", "removeTask"),
        Set.of("bash")  // 额外黑名单：不允许bash（防止写操作）
    ),

    /**
     * 探索Agent - 只读模式，用于快速搜索代码
     *
     * <p>用于快速搜索代码库、查找文件、回答关于代码的问题。
     * 不能修改文件或执行写操作。
     * allowedTools 参数会被忽略。
     * 支持任务规划工具，便于探索大型代码库时分解任务。
     */
    Explore(
        "探索Agent，用于快速搜索代码库、查找文件、回答问题。只读模式。支持任务规划。",
        Set.of("toolSearch", "getCurrentTime", "grep", "glob", "read",
                "createPlan", "updateStatus", "addTask", "removeTask"),
        Set.of("bash")  // 额外黑名单：不允许bash
    ),

    /**
     * 简历项目业务探索Agent - 只读模式，用于项目经历业务化深挖
     *
     * <p>用于从 OpenViking / GitHub / README / 源码证据中探索业务场景、用户痛点、用户流程、
     * 技术动作、X占位指标和简历 bullet 候选。缺少现成业务故事时，也应基于真实功能链路构造优化故事。
     * 它不生成最终简历、不发布 artifact、
     * 不写入记忆或 OpenViking，不向用户提问，也不能再派发子Agent。
     */
    ResumeBusinessExplore(
        "简历项目业务探索Agent，只读模式。用于把项目证据提炼成业务场景、用户痛点、用户流程、技术动作、X占位指标和候选简历bullet；缺少现成业务故事时，基于真实功能链路构造优化故事，避免架构名词堆砌。",
        Set.of(
                "toolSearch", "getCurrentTime",
                "createPlan", "updateStatus", "addTask", "removeTask",
                "grep", "glob", "read",
                "openviking_list", "openviking_tree", "openviking_read",
                "openviking_glob", "openviking_grep", "openviking_find", "openviking_search",
                "openviking_skill_search", "openviking_skill_read", "openviking_skill_files", "openviking_skill_read_file",
                "readUserMemory", "readUserMemoryDetail"
        ),
        Set.of(
                "bash",
                "publishArtifact",
                "generateMindmap",
                "getResumeGuide",
                "getOptimizeGuide",
                "evaluateResume",
                "openviking_forget",
                "openviking_skill_add",
                "rememberUserMemory",
                "rememberUserPreference"
        )
    );

    /**
     * 子Agent全局黑名单：所有子Agent都不可使用的工具
     *
     * <p>这些工具子Agent不可使用：
     * <ul>
     *   <li>spawnAgent: 子Agent不可再派发任务（防止递归嵌套）</li>
     *   <li>askUserQuestion: 子Agent不可向用户提问</li>
     *   <li>askMultipleQuestions: 子Agent不可向用户提问</li>
     *   <li>askQuestionnaire: 子Agent不可向用户提问</li>
     * </ul>
     */
    public static final Set<String> GLOBAL_EXCLUDED_TOOLS = Set.of(
        "spawnAgent",
        "askUserQuestion",
        "askMultipleQuestions",
        "askQuestionnaire"
    );

    private final String description;
    private final Set<String> allowedTools;
    private final Set<String> disallowedTools;

    SubAgentType(String description, Set<String> allowedTools, Set<String> disallowedTools) {
        this.description = description;
        this.allowedTools = allowedTools;
        this.disallowedTools = disallowedTools;
    }

    /**
     * 获取类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取允许使用的工具白名单
     *
     * <p>如果包含 "*"，表示允许所有工具。
     */
    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    /**
     * 获取禁止使用的工具黑名单
     *
     * <p>这是Per-Agent级别的黑名单，会与全局黑名单合并。
     */
    public Set<String> getDisallowedTools() {
        return disallowedTools;
    }

    /**
     * 是否支持自定义工具列表
     *
     * <p>只有 General 类型支持通过 allowedTools 参数自定义工具。
     */
    public boolean supportsCustomTools() {
        return this == General;
    }

    /**
     * 是否为只读模式
     */
    public boolean isReadOnly() {
        return this == Plan || this == Explore || this == ResumeBusinessExplore;
    }

    /**
     * 是否应严格按白名单暴露工具。
     *
     * <p>General 维持原有通用/自定义工具行为；其他预定义类型必须精确匹配白名单，
     * 避免核心工具默认注入导致只读Agent拿到发布、写入或删除能力。
     */
    public boolean usesExactToolWhitelist() {
        return this != General;
    }

    /**
     * 宽松解析子Agent类型，兼容大小写、连字符和下划线。
     */
    public static SubAgentType fromName(String value) {
        if (value == null || value.isBlank()) {
            return General;
        }

        String normalized = normalizeName(value);
        for (SubAgentType type : values()) {
            if (normalizeName(type.name()).equals(normalized)) {
                return type;
            }
        }
        return General;
    }

    private static String normalizeName(String value) {
        return value.trim()
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }
}
