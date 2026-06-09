package com.msz.resume.ai.chat.prompt.builder;

import com.msz.resume.ai.chat.prompt.model.PromptResult;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.Set;

/**
 * 系统提示词构建器接口
 *
 * <h2>业务背景</h2>
 * <p>每次调用LLM之前，都需要构建一个"系统提示词"，告诉LLM：
 * <ul>
 *   <li>你是谁？（身份定义）</li>
 *   <li>你应该怎么说话？（语气风格）</li>
 *   <li>你能用什么工具？（工具使用指南）</li>
 *   <li>当前用户是谁？（用户上下文）</li>
 * </ul>
 *
 * <h2>为什么需要构建器？</h2>
 * <p>以前的系统提示词是一个写死的字符串，改一个字都要改代码重新部署。
 * 现在把系统提示词拆分成多个section，每个section可以独立配置、独立开关，
 * 支持热更新，方便调试和优化。
 *
 * <h2>静态/动态分离</h2>
 * <p>系统提示词分为两部分：
 * <ul>
 *   <li><b>静态部分</b>：Intro、Tone and Style、Output Efficiency、Using Your Tools
 *       → 这些内容很少变化，可以缓存预热</li>
 *   <li><b>动态部分</b>：session_guidance、env_info、user_context、user_preferences
 *       → 这些内容每次请求都可能不同，需要实时生成</li>
 * </ul>
 * <p>两部分用分隔符 {@code __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__} 分开，便于调试。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 最常用的方式（80%场景）
 * PromptResult result = promptBuilder.build();
 * SystemMessage systemMessage = SystemMessage.from(result.systemPrompt());
 *
 * // 缓存预热
 * String staticPart = promptBuilder.buildStatic();
 * cache.put("static_prompt", staticPart);
 *
 * // 调试
 * if (promptBuilder.isSectionEnabled("memory")) {
 *     log.debug("Memory section: {}", promptBuilder.getSection("memory"));
 * }
 * }</pre>
 *
 * <h2>核心方法</h2>
 * <ul>
 *   <li><b>build()</b>: 构建完整系统提示词，返回 PromptResult（包含静态+动态+token估算）</li>
 *   <li><b>build(UserProfile)</b>: 构建完整系统提示词，注入用户上下文</li>
 *   <li><b>buildSubAgent(...)</b>: 构建子Agent专用系统提示词（精简版，受限工具集）</li>
 *   <li><b>buildStatic()</b>: 只构建静态部分，用于缓存预热</li>
 *   <li><b>buildDynamic(UserProfile)</b>: 只构建动态部分，传入用户上下文</li>
 *   <li><b>getSection(name)</b>: 获取单个section内容，用于调试</li>
 *   <li><b>isSectionEnabled(name)</b>: 检查section是否启用</li>
 *   <li><b>reload()</b>: 热更新配置，无需重启应用</li>
 * </ul>
 */
public interface SystemPromptBuilder {

    PromptResult build();

    PromptResult build(UserProfile userContext);

    /**
     * 构建子Agent专用系统提示词
     *
     * <p>子Agent模式下需要精简的提示词：
     * <ul>
     *   <li>不包含 persona/style 等人设信息</li>
     *   <li>使用 sub_agent_context 静态section替代 intro</li>
     *   <li>动态部分只包含 env_info 和受限版 session_guidance</li>
     *   <li>追加任务描述作为明确指令</li>
     * </ul>
     *
     * @param taskDescription 子Agent的任务描述
     * @param userContext 用户上下文信息
     * @param toolRegistry 工具注册表
     * @param permittedTools 允许使用的工具名称集合
     * @return 包含精简系统提示词的 PromptResult
     */
    PromptResult buildSubAgent(String taskDescription, UserProfile userContext,
                               ToolRegistry toolRegistry, Set<String> permittedTools);

    PromptResult buildSubAgent(String taskDescription, UserProfile userContext,
                               ToolRegistry toolRegistry, List<ToolSpecification> permittedToolSpecs);

    String buildStatic();

    String buildDynamic(UserProfile userContext);

    String getSection(String sectionName);

    boolean isSectionEnabled(String sectionName);

    void reload();

    /**
     * 清除静态部分缓存
     *
     * <p>当静态内容需要更新时调用，下次 buildStatic() 会重新计算。
     * reload() 方法会自动调用此方法。
     *
     * <p>使用场景：
     * <ul>
     *   <li>配置热更新后</li>
     *   <li>工具列表变化后（如新增延迟工具的使用指南）</li>
     * </ul>
     */
    void invalidateStaticCache();
}
