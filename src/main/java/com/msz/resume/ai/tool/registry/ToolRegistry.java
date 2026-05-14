package com.msz.resume.ai.tool.registry;

import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心（适配LangChain4j标准）
 *
 * 核心功能：
 * 1. 存储工具规格（ToolSpecification）：描述工具的名称、描述、参数
 * 2. 存储工具执行器（ToolExecutor）：实际执行工具逻辑
 * 3. 存储工具实例（toolInstances）：用于获取 prompt 等元信息
 * 4. 分区管理核心工具和延迟工具
 *
 * 工作原理：
 * - 大模型根据ToolSpecification决定调用哪个工具
 * - ExecuteToolNode根据工具名找到对应的ToolExecutor执行
 * - 核心工具（@CoreTool）始终加载，延迟工具按需加载
 */
@Slf4j
@Component
public class ToolRegistry {

    public enum ToolExposure {
        CORE,
        DEFERRED
    }

    /**
     * 工具规格映射
     * key: 工具名称
     * value: 工具规格（包含名称、描述、参数定义）
     */
    private final Map<String, ToolSpecification> toolSpecifications = new ConcurrentHashMap<>();

    /**
     * 工具执行器映射
     * key: 工具名称
     * value: 工具执行器（包含实际的执行逻辑）
     */
    private final Map<String, ToolExecutor> toolExecutors = new ConcurrentHashMap<>();

    /**
     * 工具实例映射
     * key: 工具名称
     * value: 工具实例对象（用于获取 prompt 等元信息）
     */
    private final Map<String, Object> toolInstances = new ConcurrentHashMap<>();

    /**
     * 核心工具名称集合（使用LinkedHashSet保证顺序稳定）
     * 标注了 @CoreTool 的工具始终完整加载
     *
     * <p>顺序稳定性对于前缀缓存至关重要：工具schema数组的顺序变化会导致缓存失效。
     */
    private final Set<String> coreToolNames = new LinkedHashSet<>();

    /**
     * 延迟工具名称集合（使用LinkedHashSet保证顺序稳定）
     * 未标注 @CoreTool 的工具按需加载
     *
     * <p>顺序稳定性对于前缀缓存至关重要：工具schema数组的顺序变化会导致缓存失效。
     */
    private final Set<String> deferredToolNames = new LinkedHashSet<>();

    /**
     * 注册工具（完整参数）
     */
    public void registerTool(String name, ToolSpecification specification, ToolExecutor executor) {
        toolSpecifications.put(name, specification);
        toolExecutors.put(name, executor);
        toolInstances.put(name, executor);
        log.info("[工具注册] 注册工具: {}", name);
    }

    /**
     * 注册外部工具，并显式指定核心/延迟分区。
     *
     * <p>MCP 等外部工具没有 @CoreTool 注解，需要通过配置决定是否进入核心工具集。
     */
    public void registerTool(String name,
                             ToolSpecification specification,
                             ToolExecutor executor,
                             ToolExposure exposure,
                             Object instance) {
        toolSpecifications.put(name, specification);
        toolExecutors.put(name, executor);
        toolInstances.put(name, instance != null ? instance : executor);

        if (ToolExposure.CORE.equals(exposure)) {
            coreToolNames.add(name);
            deferredToolNames.remove(name);
        } else {
            deferredToolNames.add(name);
            coreToolNames.remove(name);
        }

        log.info("[工具注册] 注册外部工具: {}, exposure={}", name, exposure);
    }

    /**
     * 从对象自动提取并注册工具
     *
     * 检测工具类是否标注 @CoreTool 注解，自动分配到核心/延迟集合
     *
     * @param toolObject 包含@Tool注解方法的对象
     */
    public void registerToolsFromObject(Object toolObject) {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(toolObject);
        boolean isCoreTool = toolObject.getClass().isAnnotationPresent(CoreTool.class);

        for (ToolSpecification spec : specs) {
            for (Method method : toolObject.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification methodSpec = ToolSpecifications.toolSpecificationFrom(method);
                    if (methodSpec.name().equals(spec.name())) {
                        ToolExecutor executor = new DefaultToolExecutor(toolObject, method);
                        toolSpecifications.put(spec.name(), spec);
                        toolExecutors.put(spec.name(), executor);
                        toolInstances.put(spec.name(), toolObject);

                        // 分区存储工具名称
                        if (isCoreTool) {
                            coreToolNames.add(spec.name());
                            log.info("[工具注册] 核心工具: {}", spec.name());
                        } else {
                            deferredToolNames.add(spec.name());
                            log.info("[工具注册] 延迟工具: {}", spec.name());
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * 获取工具规格
     */
    public ToolSpecification getToolSpecification(String name) {
        return toolSpecifications.get(name);
    }

    /**
     * 获取工具执行器
     */
    public ToolExecutor getToolExecutor(String name) {
        return toolExecutors.get(name);
    }

    /**
     * 获取所有工具规格
     */
    public List<ToolSpecification> getAllToolSpecifications() {
        return new ArrayList<>(toolSpecifications.values());
    }

    /**
     * 获取所有工具名称
     */
    public Set<String> getAllToolNames() {
        return toolSpecifications.keySet();
    }

    /**
     * 获取所有工具实例
     *
     * @return 工具实例列表
     */
    public List<Object> getAllToolInstances() {
        return new ArrayList<>(toolInstances.values());
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return toolSpecifications.containsKey(name);
    }

    /**
     * 获取工具数量
     */
    public int getToolCount() {
        return toolSpecifications.size();
    }

    /**
     * 移除工具
     */
    public boolean removeTool(String name) {
        ToolSpecification spec = toolSpecifications.remove(name);
        toolExecutors.remove(name);
        toolInstances.remove(name);
        coreToolNames.remove(name);
        deferredToolNames.remove(name);
        return spec != null;
    }

    /**
     * 按工具名前缀移除工具。
     */
    public List<String> removeToolsByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }

        List<String> removed = new ArrayList<>();
        for (String name : new ArrayList<>(toolSpecifications.keySet())) {
            if (name.startsWith(prefix) && removeTool(name)) {
                removed.add(name);
            }
        }
        return removed;
    }

    /**
     * 清空所有工具
     */
    public void clearAll() {
        toolSpecifications.clear();
        toolExecutors.clear();
        toolInstances.clear();
        coreToolNames.clear();
        deferredToolNames.clear();
        log.info("[工具注册] 清空所有工具");
    }

    // ==================== 分区查询方法 ====================

    /**
     * 获取核心工具规格列表
     *
     * @return 核心工具规格列表
     */
    public List<ToolSpecification> getCoreToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (String name : coreToolNames) {
            ToolSpecification spec = toolSpecifications.get(name);
            if (spec != null) {
                specs.add(spec);
            }
        }
        return specs;
    }

    /**
     * 获取延迟工具规格列表
     *
     * @return 延迟工具规格列表
     */
    public List<ToolSpecification> getDeferredToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (String name : deferredToolNames) {
            ToolSpecification spec = toolSpecifications.get(name);
            if (spec != null) {
                specs.add(spec);
            }
        }
        return specs;
    }

    /**
     * 判断是否为核心工具
     *
     * @param name 工具名称
     * @return 是否为核心工具
     */
    public boolean isCoreTool(String name) {
        return coreToolNames.contains(name);
    }

    /**
     * 获取核心工具名称集合
     *
     * @return 核心工具名称集合
     */
    public Set<String> getCoreToolNames() {
        return Collections.unmodifiableSet(coreToolNames);
    }

    /**
     * 获取延迟工具名称集合
     *
     * @return 延迟工具名称集合
     */
    public Set<String> getDeferredToolNames() {
        return Collections.unmodifiableSet(deferredToolNames);
    }

    // ==================== 延迟工具搜索方法 ====================

    /**
     * 获取延迟工具的轻量描述列表
     *
     * @return 延迟工具的 name + description 列表
     */
    public List<ToolHint> getDeferredToolHints() {
        List<ToolHint> hints = new ArrayList<>();
        for (String name : deferredToolNames) {
            ToolSpecification spec = toolSpecifications.get(name);
            if (spec != null) {
                hints.add(new ToolHint(name, spec.description()));
            }
        }
        return hints;
    }

    /**
     * 按名称精确获取延迟工具规格
     *
     * @param toolName 工具名称
     * @return 工具规格，不存在则返回 null
     */
    public ToolSpecification getDeferredToolSpecification(String toolName) {
        if (deferredToolNames.contains(toolName)) {
            return toolSpecifications.get(toolName);
        }
        return null;
    }

    /**
     * 按名称列表获取延迟工具规格
     *
     * @param toolNames 工具名称列表
     * @return 工具规格列表
     */
    public List<ToolSpecification> getDeferredToolSpecifications(Collection<String> toolNames) {
        List<ToolSpecification> specs = new ArrayList<>();
        for (String name : toolNames) {
            if (deferredToolNames.contains(name)) {
                ToolSpecification spec = toolSpecifications.get(name);
                if (spec != null) {
                    specs.add(spec);
                }
            }
        }
        return specs;
    }

    /**
     * 合并核心工具和已发现的延迟工具规格，内部去重
     *
     * @param discoveredToolNames 已发现的延迟工具名称集合，可为 null 或空
     * @return 合并后的工具规格列表（核心在前，延迟在后，按名称去重）
     */
    public List<ToolSpecification> getAllSpecifications(Set<String> discoveredToolNames) {
        // 核心工具优先，保证顺序稳定（对前缀缓存友好）
        List<ToolSpecification> specs = new ArrayList<>(getCoreToolSpecifications());

        if (discoveredToolNames != null && !discoveredToolNames.isEmpty()) {
            List<ToolSpecification> discoveredSpecs = getDeferredToolSpecifications(discoveredToolNames);
            specs.addAll(discoveredSpecs);
            log.debug("[ToolRegistry] 合并工具规格: 核心={}, 延迟={}, 总计={}",
                    coreToolNames.size(), discoveredSpecs.size(), specs.size());
        }

        // 按名称去重
        Set<String> seen = new LinkedHashSet<>();
        return specs.stream()
                .filter(spec -> seen.add(spec.name()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 搜索延迟工具（模糊匹配 name + description）
     *
     * @param query 搜索关键词
     * @return 匹配的工具提示列表
     */
    public List<ToolHint> searchDeferredTools(String query) {
        if (query == null || query.isBlank()) {
            return getDeferredToolHints();
        }

        String lowerQuery = query.toLowerCase();
        List<ToolHint> results = new ArrayList<>();

        for (String name : deferredToolNames) {
            ToolSpecification spec = toolSpecifications.get(name);
            if (spec != null) {
                boolean nameMatches = name.toLowerCase().contains(lowerQuery);
                boolean descMatches = spec.description() != null &&
                        spec.description().toLowerCase().contains(lowerQuery);

                if (nameMatches || descMatches) {
                    results.add(new ToolHint(name, spec.description()));
                }
            }
        }
        return results;
    }

    /**
     * 根据工具名称集合获取工具规格
     *
     * 用于子Agent模式：合并核心工具 + 指定名称的延迟工具，排除不需要的工具。
     * 核心工具始终包含（但排除在 excludedTools 中的），延迟工具只在 toolNames 中包含时才加载。
     *
     * @param toolNames 允许使用的工具名称集合（null 或空表示只加载核心工具）
     * @param excludedTools 需要排除的工具名称集合（如 delegateTask、askUserQuestion）
     * @return 合并后的工具规格列表
     */
    public List<ToolSpecification> getSpecificationsForToolNames(Set<String> toolNames, Set<String> excludedTools) {
        List<ToolSpecification> specs = new ArrayList<>();

        // 核心工具：排除在 excludedTools 中的
        for (String name : coreToolNames) {
            if (excludedTools != null && excludedTools.contains(name)) {
                continue;
            }
            ToolSpecification spec = toolSpecifications.get(name);
            if (spec != null) {
                specs.add(spec);
            }
        }

        // 延迟工具：只在 toolNames 中明确包含的
        if (toolNames != null && !toolNames.isEmpty()) {
            for (String name : toolNames) {
                // 跳过排除的工具
                if (excludedTools != null && excludedTools.contains(name)) {
                    continue;
                }
                // 跳过已经是核心工具的（避免重复）
                if (coreToolNames.contains(name)) {
                    continue;
                }
                ToolSpecification spec = toolSpecifications.get(name);
                if (spec != null) {
                    specs.add(spec);
                }
            }
        }

        return specs;
    }

    /**
     * 获取核心工具实例列表
     *
     * 只返回标注了 @CoreTool 的工具实例，用于 ToolPromptCollector 收集 prompt
     *
     * @return 核心工具实例列表
     */
    public List<Object> getCoreToolInstances() {
        List<Object> instances = new ArrayList<>();
        for (String name : coreToolNames) {
            Object instance = toolInstances.get(name);
            if (instance != null) {
                instances.add(instance);
            }
        }
        return instances;
    }
}
