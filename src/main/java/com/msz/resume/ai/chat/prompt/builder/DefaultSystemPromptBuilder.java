package com.msz.resume.ai.chat.prompt.builder;

import com.msz.resume.ai.chat.prompt.config.PromptConfigLoader;
import com.msz.resume.ai.chat.prompt.model.PromptResult;
import com.msz.resume.ai.chat.prompt.model.SectionName;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.chat.prompt.provider.DynamicSectionProvider;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 总指挥 职责：组装完整的系统提示词
 *
 * 默认系统提示词构建器实现
 *
 * <h2>业务背景</h2>
 * <p>这是系统提示词构建的核心实现类。CallLlmNode 在调用 LLM 之前会调用这个类，
 * 生成完整的系统提示词。
 *
 * <h2>组装流程</h2>
 * <pre>
 * build() 调用流程:
 *
 * 1. 构建静态部分:
 *    Intro section (from YAML)
 *    + Tone and Style section (from YAML)
 *    + Output Efficiency section (from YAML)
 *    + Using Your Tools section (from YAML，通用指南)
 *
 * 2. 插入分隔符: __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__
 *
 * 3. 构建动态部分:
 *    session_guidance (from DynamicSectionProvider)
 *    + env_info (from DynamicSectionProvider)
 *    + user_context (from DynamicSectionProvider)
 *    + user_preferences (from DynamicSectionProvider)
 *    + memory (预留，暂时为空)
 *
 * 4. 返回 PromptResult:
 *    - prompt: 完整系统提示词
 *    - staticPart: 静态部分（可用于缓存）
 *    - dynamicPart: 动态部分
 *    - tokenEstimate: token估算值
 * </pre>
 *
 * <h2>依赖关系</h2>
 * <pre>
 * DefaultSystemPromptBuilder
 *   ├── PromptConfigLoader      → 加载YAML配置，获取section模板和开关
 *   ├── DynamicSectionProvider  → 生成动态section内容
 *   └── ToolRegistry            → 工具注册表（传给Provider）
 * </pre>
 *
 * <h2>工具说明</h2>
 * <p>工具的具体描述通过 toolSpecifications 发送给 LLM，不在此处拼接。
 * using-your-tools.yml 只包含通用的工具使用指南（如"参数缺失时不要调用"）。
 *
 * <h2>空值处理</h2>
 * <p>如果某个section被禁用或返回空字符串，会自动跳过，不会产生多余空行。
 *
 * <h2>分隔符用途</h2>
 * <p>分隔符 {@code __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__}} 用于：
 * <ul>
 *   <li>调试时区分静态/动态部分</li>
 *   <li>后续实现 Prompt Caching 时标识可缓存边界</li>
 * </ul>
 */
@Component
public class DefaultSystemPromptBuilder implements SystemPromptBuilder {

    private final PromptConfigLoader configLoader;
    private final DynamicSectionProvider dynamicSectionProvider;
    private final ToolRegistry toolRegistry;

    /** 静态部分缓存（volatile保证可见性） */
    private volatile String staticCache = null;

    public DefaultSystemPromptBuilder(
            PromptConfigLoader configLoader,
            DynamicSectionProvider dynamicSectionProvider,
            ToolRegistry toolRegistry) {
        this.configLoader = configLoader;
        this.dynamicSectionProvider = dynamicSectionProvider;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public PromptResult build() {
        return build(UserProfile.empty());
    }

    /**
     * 构建完整系统提示词（带用户上下文）
     */
    public PromptResult build(UserProfile userContext) {
        String staticPart = buildStatic();
        String dynamicPart = buildDynamic(userContext);

        StringBuilder sb = new StringBuilder();
        sb.append(staticPart);

        if (!dynamicPart.isBlank()) {
            sb.append("\n\n").append(PromptResult.BOUNDARY).append("\n\n");
            sb.append(dynamicPart);
        }

        String fullPrompt = sb.toString().trim();
        int tokenEstimate = estimateTokens(fullPrompt);

        return new PromptResult(fullPrompt, staticPart, dynamicPart, tokenEstimate);
    }

    /**
     * 构建子Agent专用系统提示词
     *
     * <p>设计原则：复用父级静态前缀，确保 prefix cache 命中。
     * 子Agent的提示词结构：
     * <pre>
     * [intro] [tone_and_style] [output_efficiency] [using_your_tools]  ← 完全复用父级静态前缀（缓存命中）
     * ─── __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__ ───
     * [sub_agent_context(任务描述+约束)] + [session_guidance(受限工具集)] + [env_info]
     * （不含 user_context / user_preferences / memory）
     * </pre>
     *
     * @param taskDescription 子Agent的任务描述
     * @param userContext 用户上下文信息（子Agent模式下不使用，保留参数以兼容接口）
     * @param toolRegistry 工具注册表
     * @param permittedTools 允许使用的工具名称集合
     * @return 包含精简系统提示词的 PromptResult
     */
    @Override
    public PromptResult buildSubAgent(String taskDescription, UserProfile userContext,
                                      ToolRegistry toolRegistry, Set<String> permittedTools) {
        // 复用父级静态前缀（确保 prefix cache 命中）
        String staticPart = buildStatic();
        String dynamicPart = buildSubAgentDynamic(taskDescription, toolRegistry, permittedTools);

        StringBuilder sb = new StringBuilder();
        sb.append(staticPart);

        if (!dynamicPart.isBlank()) {
            sb.append("\n\n").append(PromptResult.BOUNDARY).append("\n\n");
            sb.append(dynamicPart);
        }

        String fullPrompt = sb.toString().trim();
        int tokenEstimate = estimateTokens(fullPrompt);

        return new PromptResult(fullPrompt, staticPart, dynamicPart, tokenEstimate);
    }

    /**
     * 构建子Agent动态部分
     *
     * <p>只包含：sub_agent_context + restricted session_guidance + env_info
     * 不包含：user_context、user_preferences、memory
     */
    private String buildSubAgentDynamic(String taskDescription, ToolRegistry toolRegistry,
                                         Set<String> permittedTools) {
        StringBuilder sb = new StringBuilder();

        // 1. sub_agent_context：任务描述 + 约束
        appendIfNotEmpty(sb, dynamicSectionProvider.getSubAgentContext(taskDescription));

        // 2. session_guidance：受限工具集
        appendIfNotEmpty(sb, dynamicSectionProvider.getSessionGuidance(toolRegistry, permittedTools));

        // 3. env_info：模型信息（子Agent也需要知道自己是什么模型）
        appendIfNotEmpty(sb, dynamicSectionProvider.getEnvInfo());

        return sb.toString().trim();
    }

    /**
     * 构建静态部分
     *
     * <p>使用会话级缓存：首次计算后缓存，后续直接返回。
     * 调用 invalidateStaticCache() 或 reload() 会清除缓存。
     *
     * <p>注意：工具的具体描述通过 toolSpecifications 发送给 LLM，
     * 此处只加载 using-your-tools.yml 中的通用使用指南。
     */
    @Override
    public String buildStatic() {
        // 检查缓存
        if (staticCache != null) {
            return staticCache;
        }

        // 双重检查锁定（线程安全）
        synchronized (this) {
            if (staticCache != null) {
                return staticCache;
            }

            StringBuilder sb = new StringBuilder();

            // 加载静态section模板（包括 using-your-tools.yml）
            for (String sectionName : SectionName.staticSections()) {
                if (configLoader.isSectionEnabled(sectionName)) {
                    String template = configLoader.loadSectionTemplate(sectionName);
                    appendIfNotEmpty(sb, template);
                }
            }

            staticCache = sb.toString().trim();
            return staticCache;
        }
    }

    @Override
    public String buildDynamic(UserProfile userContext) {
        StringBuilder sb = new StringBuilder();


        //备注:以后消息压缩管道优化可以改这里！
        appendIfNotEmpty(sb, dynamicSectionProvider.getEnvInfo());
        appendIfNotEmpty(sb, dynamicSectionProvider.getSessionGuidance(toolRegistry));
        appendIfNotEmpty(sb, dynamicSectionProvider.getUserProfile(userContext));
        appendIfNotEmpty(sb, dynamicSectionProvider.getUserPreferences(userContext));
        appendIfNotEmpty(sb, dynamicSectionProvider.getMemory(userContext));

        return sb.toString().trim();
    }

    @Override
    public String getSection(String sectionName) {
        if (!configLoader.isSectionEnabled(sectionName)) {
            return "";
        }

        // 静态section：从配置加载
        if (SectionName.staticSections().contains(sectionName)) {
            return configLoader.loadSectionTemplate(sectionName);
        }

        // 动态section：从provider获取
        return switch (sectionName) {
            case SectionName.SESSION_GUIDANCE -> dynamicSectionProvider.getSessionGuidance(toolRegistry);
            case SectionName.ENV_INFO -> dynamicSectionProvider.getEnvInfo();
            case SectionName.USER_CONTEXT -> dynamicSectionProvider.getUserProfile(UserProfile.empty());
            case SectionName.USER_PREFERENCES -> dynamicSectionProvider.getUserPreferences(UserProfile.empty());
            case SectionName.MEMORY -> dynamicSectionProvider.getMemory(UserProfile.empty());
            default -> "";
        };
    }

    @Override
    public boolean isSectionEnabled(String sectionName) {
        return configLoader.isSectionEnabled(sectionName);
    }

    @Override
    public void reload() {
        configLoader.reload();
        invalidateStaticCache();
    }

    @Override
    public void invalidateStaticCache() {
        synchronized (this) {
            staticCache = null;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 只有内容不为空时才拼接
     */
    private void appendIfNotEmpty(StringBuilder sb, String content) {
        if (content != null && !content.isBlank()) {
            sb.append(content).append("\n\n");
        }
    }

    /**
     * Token估算（简单实现）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 中文约1.5字符/token，英文约4字符/token，取中间值
        return text.length() / 2;
    }
}
