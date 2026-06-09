package com.msz.resume.ai.chat.prompt.provider;

import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.Set;

/**
 * 动态Section提供者接口
 *
 * <h2>业务背景</h2>
 * <p>系统提示词分为"静态部分"和"动态部分"。静态部分（Intro、Tone and Style等）
 * 基本不变，可以缓存。动态部分每次请求都可能变化，需要实时生成。
 *
 * <p>这个接口负责提供动态部分的内容，包括：
 * <ul>
 *   <li>session_guidance - 告诉LLM当前会话该用什么工具</li>
 *   <li>env_info - 告诉LLM当前模型名称、知识截止日期</li>
 *   <li>user_context - 告诉LLM当前用户是谁</li>
 *   <li>user_preferences - 告诉LLM用户的语言偏好、输出风格偏好</li>
 *   <li>memory - 用户历史记忆（预留）</li>
 * </ul>
 *
 * <h2>为什么需要动态Section？</h2>
 * <p>不同用户、不同会话、不同时间点，LLM需要知道的信息是不同的：
 * <ul>
 *   <li>用户A喜欢简洁输出，用户B喜欢详细解释</li>
 *   <li>上午LLM有一个工具可用，下午新增了两个工具</li>
 *   <li>同一个用户上次聊过的话题，这次应该能记得</li>
 * </ul>
 *
 * <h2>调用时机</h2>
 * <p>每次 CallLlmNode 调用 LLM 之前，都会构建完整的系统提示词，
 * 其中动态部分就是通过这个接口获取的。
 */
public interface DynamicSectionProvider {

    /**
     * 获取 session_guidance section 内容
     *
     * <p>告诉LLM当前有哪些工具可用，什么时候该用什么工具。
     *
     * @param toolRegistry 工具注册表
     * @return session_guidance section 内容
     */
    String getSessionGuidance(ToolRegistry toolRegistry);

    /**
     * 获取 env_info section 内容
     *
     * <p>告诉LLM当前模型信息，包括模型名称和知识截止日期。
     *
     * @return env_info section 内容
     */
    String getEnvInfo();

    /**
     * 获取 user_context section 内容
     *
     * <p>告诉LLM当前用户是谁，注入用户身份信息。
     *
     * @param userContext 用户上下文信息
     * @return user_context section 内容
     */
    String getUserProfile(UserProfile userContext);

    /**
     * 获取 user_preferences section 内容
     *
     * <p>告诉LLM用户的偏好设置，如语言偏好、输出风格等。
     *
     * @param userContext 用户上下文信息
     * @return user_preferences section 内容
     */
    String getUserPreferences(UserProfile userContext);

    /**
     * 获取 memory section 内容（预留）
     *
     * <p>后续实现用户记忆功能，目前返回空字符串。
     *
     * @param userContext 用户上下文信息
     * @return memory section 内容，当前返回空字符串
     */
    default String getMemory(UserProfile userContext) {
        return "";
    }

    /**
     * 获取受限工具集的 session_guidance section 内容
     *
     * <p>用于子Agent模式：只展示允许使用的工具，不展示被排除的工具。
     *
     * @param toolRegistry 工具注册表
     * @param permittedTools 允许使用的工具名称集合
     * @return session_guidance section 内容
     */
    default String getSessionGuidance(ToolRegistry toolRegistry, Set<String> permittedTools) {
        // 默认实现回退到完整版本
        return getSessionGuidance(toolRegistry);
    }

    default String getSessionGuidance(List<ToolSpecification> permittedToolSpecs) {
        return "";
    }

    /**
     * 获取 sub_agent_context section 内容
     *
     * <p>用于子Agent模式：生成包含任务描述和约束的提示词section。
     * 模板从 sub-agent-context.yml 加载，{taskDescription} 占位符替换为实际任务描述。
     *
     * @param taskDescription 子Agent的任务描述
     * @return sub_agent_context section 内容
     */
    String getSubAgentContext(String taskDescription);

}
