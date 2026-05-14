package com.msz.resume.ai.agent;

import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 子Agent类型注册中心
 *
 * <p>子Agent工具解析的唯一入口，管理预定义Agent类型的工具配置，
 * 负责根据Agent类型解析最终的工具规格列表。
 *
 * <h2>工具过滤流程</h2>
 * <pre>
 * 1. 合并黑名单：全局黑名单（GLOBAL_EXCLUDED_TOOLS） + Per-Agent黑名单
 * 2. 解析白名单：自定义工具 或 预定义白名单（如果包含 "*"，则使用所有可用工具）
 * 3. 过滤工具：调用 ToolRegistry.getSpecificationsForToolNames()
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * SubAgentTypeRegistry registry = new SubAgentTypeRegistry();
 * // 预定义类型
 * List<ToolSpecification> tools = registry.getToolSpecifications(SubAgentType.Explore, toolRegistry, null);
 * // General + 自定义工具
 * List<ToolSpecification> tools = registry.getToolSpecifications(SubAgentType.General, toolRegistry, customTools);
 * }</pre>
 *
 * @see SubAgentType
 * @see ToolRegistry
 */
@Slf4j
@Component
public class SubAgentTypeRegistry {

    /**
     * 解析允许使用的工具集合
     *
     * <p>规则：
     * <ul>
     *   <li>General + 自定义工具：返回自定义工具集合</li>
     *   <li>白名单包含 "*"：返回所有可用工具</li>
     *   <li>其他：返回预定义白名单</li>
     * </ul>
     *
     * @param type Agent类型
     * @param toolRegistry 工具注册中心
     * @param customTools 自定义工具名称集合（仅General类型使用）
     * @return 允许使用的工具名称集合
     */
    public Set<String> resolveAllowedTools(SubAgentType type, ToolRegistry toolRegistry, Set<String> customTools) {
        // General + 自定义工具
        if (type.supportsCustomTools() && customTools != null && !customTools.isEmpty()) {
            log.debug("[SubAgentTypeRegistry] Agent类型 {} 使用自定义工具: {}", type, customTools);
            return customTools;
        }

        // 获取所有可用工具名称
        Set<String> allToolNames = new HashSet<>();
        allToolNames.addAll(toolRegistry.getCoreToolNames());
        allToolNames.addAll(toolRegistry.getDeferredToolNames());

        // 白名单为 "*" 表示所有工具
        Set<String> allowed = type.getAllowedTools();
        if (allowed.contains("*")) {
            log.debug("[SubAgentTypeRegistry] Agent类型 {} 白名单为 *，返回所有工具", type);
            return allToolNames;
        }

        log.debug("[SubAgentTypeRegistry] Agent类型 {} 白名单: {}", type, allowed);
        return allowed;
    }

    /**
     * 获取最终的工具规格列表
     *
     * <p>这是子Agent工具解析的唯一入口，处理所有情况（包括General + 自定义工具）。
     *
     * @param type Agent类型
     * @param toolRegistry 工具注册中心
     * @return 工具规格列表
     */
    public List<ToolSpecification> getToolSpecifications(SubAgentType type, ToolRegistry toolRegistry) {
        return getToolSpecifications(type, toolRegistry, null);
    }

    /**
     * 获取最终的工具规格列表（支持General类型的自定义工具）
     *
     * <p>流程：
     * <ol>
     *   <li>合并黑名单：全局黑名单 + Per-Agent黑名单</li>
     *   <li>解析白名单：自定义工具 或 预定义白名单</li>
     *   <li>过滤工具：调用 ToolRegistry.getSpecificationsForToolNames()</li>
     * </ol>
     *
     * @param type Agent类型
     * @param toolRegistry 工具注册中心
     * @param customTools 自定义工具名称集合（仅General类型使用）
     * @return 工具规格列表
     */
    public List<ToolSpecification> getToolSpecifications(
            SubAgentType type,
            ToolRegistry toolRegistry,
            Set<String> customTools) {

        // 1. 合并黑名单：全局 + Per-Agent
        Set<String> allDisallowed = new HashSet<>();
        allDisallowed.addAll(SubAgentType.GLOBAL_EXCLUDED_TOOLS);
        allDisallowed.addAll(type.getDisallowedTools());
        log.debug("[SubAgentTypeRegistry] Agent类型 {} 合并黑名单: {}", type, allDisallowed);

        // 2. 解析白名单
        Set<String> allowedTools = resolveAllowedTools(type, toolRegistry, customTools);

        // 3. 过滤工具（结合白名单和黑名单）
        List<ToolSpecification> specs = toolRegistry.getSpecificationsForToolNames(allowedTools, allDisallowed);
        log.info("[SubAgentTypeRegistry] Agent类型 {} 最终工具数量: {}", type, specs.size());

        return specs;
    }

    /**
     * 获取Agent类型的描述信息（用于日志和调试）
     *
     * @param type Agent类型
     * @return 描述字符串
     */
    public String getTypeInfo(SubAgentType type) {
        return String.format("类型: %s, 描述: %s, 只读: %s, 自定义工具: %s",
                type.name(),
                type.getDescription(),
                type.isReadOnly(),
                type.supportsCustomTools());
    }
}
