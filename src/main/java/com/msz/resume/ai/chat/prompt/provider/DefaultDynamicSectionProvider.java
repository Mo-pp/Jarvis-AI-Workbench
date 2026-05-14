package com.msz.resume.ai.chat.prompt.provider;

import com.msz.resume.ai.agent.SubAgentType;
import com.msz.resume.ai.chat.llm.config.LLMConfig;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingMemoryService;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingUserMemoryService;
import com.msz.resume.ai.chat.prompt.config.PromptConfigLoader;
import com.msz.resume.ai.chat.prompt.model.SectionName;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.tool.registry.ToolHint;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 动态信息提供者 职责：生成那些"每次请求都可能不一样"的内容
 *
 *
 * 默认动态Section提供者实现
 *
 * <h2>业务背景</h2>
 * <p>每次用户发消息时，LLM需要知道"现在是什么情况"：
 * <ul>
 *   <li>你是谁？→ user_context</li>
 *   <li>你喜欢什么风格？→ user_preferences</li>
 *   <li>你现在能用什么工具？→ session_guidance</li>
 *   <li>你是什么模型？知识截止到什么时候？→ env_info</li>
 * </ul>
 *
 * <h2>各方法说明</h2>
 * <ul>
 *   <li><b>getSessionGuidance</b>: 遍历当前可用工具，生成"推荐用哪个工具、什么时候用"的建议</li>
 *   <li><b>getEnvInfo</b>: 从 LLMConfig 读取实际使用的模型名称，从 prompt 配置读取知识截止日期</li>
 *   <li><b>getUserProfile</b>: 把用户ID、用户名格式化成 "当前用户: 张三 (ID: 123)"</li>
 *   <li><b>getUserPreferences</b>: 把用户的语言偏好、输出风格格式化成配置说明</li>
 *   <li><b>getMemory</b>: 预留接口，后续实现用户记忆功能，目前返回空字符串</li>
 * </ul>
 *
 * <h2>输出示例（session_guidance）</h2>
 * <pre>
 * ## Session Guidance
 *
 * You have access to the following tools:
 * - hello_world: 用于测试工具系统
 * - mindmap: 生成思维导图
 *
 * Use tools when the user's request requires structured output or external processing.
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDynamicSectionProvider implements DynamicSectionProvider {

    private static final String LANGUAGE_STYLE_PREFERENCE_KEY = "language-style";

    private final PromptConfigLoader configLoader;
    private final LLMConfig llmConfig;
    private final OpenVikingMemoryService openVikingMemoryService;
    private final OpenVikingUserMemoryService openVikingUserMemoryService;

    @Override
    public String getSessionGuidance(ToolRegistry toolRegistry) {
        List<ToolSpecification> coreTools = toolRegistry.getCoreToolSpecifications();
        List<ToolHint> deferredHints = toolRegistry.getDeferredToolHints();

        if (coreTools.isEmpty() && deferredHints.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Session Guidance\n\n");

        // 核心工具 — 完整描述
        if (!coreTools.isEmpty()) {
            sb.append("You have direct access to the following tools:\n");
            for (ToolSpecification tool : coreTools) {
                sb.append("- ").append(tool.name());
                if (tool.description() != null && !tool.description().isBlank()) {
                    sb.append(": ").append(tool.description());
                }
                sb.append("\n");
            }
        }

        // 延迟工具 — 仅名称
        if (!deferredHints.isEmpty()) {
            sb.append("\nAdditional tools (use toolSearch to learn details):\n");
            for (ToolHint hint : deferredHints) {
                sb.append("- ").append(hint.name()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 获取受限工具集的 session_guidance
     *
     * <p>用于子Agent模式：只展示允许使用的工具，不展示被排除的工具。
     * 排除的工具包括 spawnAgent、askUserQuestion、askMultipleQuestions 等。
     */
    @Override
    public String getSessionGuidance(ToolRegistry toolRegistry, Set<String> permittedTools) {
        if (permittedTools == null || permittedTools.isEmpty()) {
            return "";
        }

        List<ToolSpecification> filteredSpecs = toolRegistry.getSpecificationsForToolNames(
            permittedTools,
            SubAgentType.GLOBAL_EXCLUDED_TOOLS
        );

        if (filteredSpecs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Session Guidance\n\n");
        sb.append("You have access to the following tools:\n");
        for (ToolSpecification tool : filteredSpecs) {
            sb.append("- ").append(tool.name());
            if (tool.description() != null && !tool.description().isBlank()) {
                sb.append(": ").append(tool.description());
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public String getEnvInfo() {
        // 从 LLMConfig 获取实际使用的模型名称（而非 system-prompt.yml 中的静态配置）
        String modelName = getActualModelName();
        String knowledgeCutoff = configLoader.getKnowledgeCutoff();
        return String.format("## Environment Info\n\n当前模型: %s\n知识截止日期: %s", modelName, knowledgeCutoff);
    }

    /**
     * 获取实际使用的模型名称
     *
     * <p>根据 provider 配置，从对应的配置中读取模型名称。
     * 这样可以确保系统提示词中的模型名称与实际调用的模型一致。
     */
    private String getActualModelName() {
        String provider = llmConfig.getProvider();
        if ("zhipu".equals(provider)) {
            return llmConfig.getZhipu().getModel();
        } else if ("dashscope".equals(provider)) {
            return llmConfig.getDashscope().getModel();
        } else if ("gpt".equals(provider)) {
            return llmConfig.getGpt().getModel();
        } else if ("qianfan-coding-plan".equals(provider) || "Qianfan_Coding_Plan".equals(provider)) {
            return llmConfig.getQianfanCodingPlan().getModel();
        }
        // 默认返回 zhipu 配置（向后兼容）
        return llmConfig.getZhipu().getModel();
    }

    @Override
    public String getUserProfile(UserProfile userContext) {
        if (userContext == null || userContext.userId() == null) {
            return "";
        }
        String username = userContext.username() != null ? userContext.username() : "匿名用户";
        return String.format("## User Context\n\n当前用户: %s (ID: %s)", username, userContext.userId());
    }

    @Override
    public String getUserPreferences(UserProfile userContext) {
        if (userContext == null) {
            return "";
        }

        String outputStyle = hasText(userContext.outputStyle()) ? userContext.outputStyle().trim() : "默认";
        String longTermLanguageStyle = readLongTermLanguageStyle(userContext);

        StringBuilder sb = new StringBuilder();
        sb.append("## User Preferences\n\n");
        sb.append("### Request Preferences\n");
        sb.append("输出风格: ").append(outputStyle);

        if (longTermLanguageStyle != null && !longTermLanguageStyle.isBlank()) {
            sb.append("\n\n");
            sb.append("### Long-term Preferences\n");
            sb.append("- language-style: ").append(longTermLanguageStyle.trim());
        }

        return sb.toString();
    }

    /**
     * 从 OpenViking 读取用户长期语言风格偏好。
     *
     * <p>这里是系统 Prompt 注入链路，不能因为外部记忆服务不可用而影响正常对话。
     * 所以读取失败时只记录日志并返回空值，让 JARVIS 继续使用本次请求里的偏好参数。</p>
     */
    private String readLongTermLanguageStyle(UserProfile userContext) {
        if (userContext.userId() == null || userContext.userId().isBlank()) {
            return null;
        }

        try {
            OpenVikingReadResponse response = openVikingMemoryService.readPreference(
                    userContext.userId(),
                    LANGUAGE_STYLE_PREFERENCE_KEY
            );
            Object result = response.result();
            if (result == null) {
                return null;
            }
            String content = result.toString();
            return content.isBlank() ? null : content;
        } catch (Exception e) {
            log.warn("[DefaultDynamicSectionProvider] 读取 OpenViking 长期偏好失败, userId={}, key={}, message={}",
                    userContext.userId(), LANGUAGE_STYLE_PREFERENCE_KEY, e.getMessage());
            return null;
        }
    }

    @Override
    public String getMemory(UserProfile userContext) {
        if (userContext == null || !hasText(userContext.userId())) {
            return "";
        }

        try {
            return openVikingUserMemoryService.loadPromptMemoryOverview(userContext.userId());
        } catch (Exception e) {
            log.warn("[DefaultDynamicSectionProvider] 加载 OpenViking 用户 Markdown 记忆失败, userId={}, message={}",
                    userContext.userId(), e.getMessage());
            return "";
        }
    }

    /**
     * 获取 sub_agent_context section 内容
     *
     * <p>用于子Agent模式：从 YAML 模板加载内容，替换 {taskDescription} 占位符。
     * 模板定义了子Agent的行为约束（不调用 spawnAgent/askUserQuestion、专注完成指定任务等）。
     *
     * <p>设计原则：sub_agent_context 作为动态section，因为每次派发任务的描述不同。
     * 但子Agent复用父级的完整静态前缀（intro/tone_and_style/output_efficiency/using_your_tools），
     * 确保 prefix cache 命中——这是子Agent调用"便宜"的关键。
     */
    @Override
    public String getSubAgentContext(String taskDescription) {
        String template = configLoader.loadSectionTemplate(SectionName.SUB_AGENT_CONTEXT);
        if (template == null || template.isBlank()) {
            // 模板未配置，使用内联兜底
            return String.format("## Sub-Agent Mode\n\nYou are operating in sub-agent mode. " +
                    "Focus entirely on completing the assigned task.\n\n### Task\n%s", taskDescription);
        }
        return template.replace("{taskDescription}", taskDescription);
    }
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
