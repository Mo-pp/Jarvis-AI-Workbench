package com.msz.resume.ai.chat.prompt.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 配置管理员 职责：组装完整的系统提示词
 *
 * YAML格式的系统提示词配置加载器
 *
 * 业务背景：
 * 系统提示词的配置存储在 YAML 文件中，包括 section 开关、模板路径、全局配置。
 * 此类负责解析这些配置并提供查询接口。
 *
 * 使用场景：
 * DefaultSystemPromptBuilder 构建系统提示词时，通过此接口获取 section 模板和配置。
 *
 * 调用链：
 * DefaultSystemPromptBuilder -> PromptConfigLoader -> YAML文件
 *
 * 配置文件结构：
 * resources/prompts/
 * ├── system-prompt.yml          # 主配置
 * └── sections/
 *     ├── intro.yml
 *     ├── tone-and-style.yml
 *     ├── output-efficiency.yml
 *     └── using-your-tools.yml
 */
@Slf4j
public class YamlPromptConfigLoader implements PromptConfigLoader {

    /**
     * 存储 section 配置
     * key: section 名称（如 intro, tone_and_style）
     * value: SectionConfig(enabled, file)
     */
    private final Map<String, SectionConfig> sectionsConfig = new HashMap<>();

    /**
     * 存储全局配置
     * key: 配置项名（如 model_name, knowledge_cutoff）
     * value: 配置值
     */
    private final Map<String, String> globalConfig = new HashMap<>();

    /**
     * 缓存已加载的 section 模板内容
     * key: section 名称
     * value: 模板内容
     */
    private final Map<String, String> templates = new HashMap<>();

    /**
     * Spring 资源加载器，用于读取 classpath 下的文件
     */
    private final ResourceLoader resourceLoader;

    /**
     * 单个 section 的配置
     */
    private record SectionConfig(boolean enabled, String file) {}

    /**
     * 主配置文件路径
     */
    private static final String MAIN_CONFIG_PATH = "classpath:prompts/system-prompt.yml";

    /**
     * 构造函数
     */
    public YamlPromptConfigLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        reload();
    }

    /**
     * 热更新配置
     *
     * 重新加载主配置文件，解析所有 section 配置和全局配置。
     * 清空现有缓存，重新解析 YAML 文件。
     */
    @Override
    public void reload() {
        // 1. 清空现有缓存
        sectionsConfig.clear();
        globalConfig.clear();
        templates.clear();

        try {
            // 2. 读取主配置文件
            Resource resource = resourceLoader.getResource(MAIN_CONFIG_PATH);
            if (!resource.exists()) {
                log.warn("[YamlPromptConfigLoader] 配置文件不存在: {}", MAIN_CONFIG_PATH);
                return;
            }

            // 3. 解析 YAML
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(resource.getInputStream());

            // 4. 解析 static_sections
            parseSections(data, "static_sections", true);

            // 5. 解析 dynamic_sections
            parseSections(data, "dynamic_sections", false);

            // 6. 解析全局配置 config
            parseGlobalConfig(data);

            log.info("[YamlPromptConfigLoader] 配置加载完成，共 {} 个 section，{} 个全局配置",
                    sectionsConfig.size(), globalConfig.size());

        } catch (IOException e) {
            log.error("[YamlPromptConfigLoader] 加载配置文件失败", e);
        }
    }

    /**
     * 解析 sections 配置（静态或动态）
     *
     * @param data YAML 数据
     * @param key 配置项 key（static_sections 或 dynamic_sections）
     * @param isStatic 是否静态 section（静态有 file 字段，动态没有）
     */
    @SuppressWarnings("unchecked")
    private void parseSections(Map<String, Object> data, String key, boolean isStatic) {
        Object sectionsObj = data.get(key);
        if (!(sectionsObj instanceof Map)) {
            return;
        }

        Map<String, Map<String, Object>> sections = (Map<String, Map<String, Object>>) sectionsObj;
        for (Map.Entry<String, Map<String, Object>> entry : sections.entrySet()) {
            String sectionName = entry.getKey();
            Map<String, Object> sectionData = entry.getValue();

            boolean enabled = sectionData.get("enabled") instanceof Boolean b && b;
            // 动态 section 也可以有 file 字段（如 sub_agent_context 有模板文件）
            String file = (String) sectionData.get("file");

            sectionsConfig.put(sectionName, new SectionConfig(enabled, file));
        }
    }

    /**
     * 解析全局配置
     */
    @SuppressWarnings("unchecked")
    private void parseGlobalConfig(Map<String, Object> data) {
        Object configObj = data.get("config");
        if (!(configObj instanceof Map)) {
            return;
        }

        Map<String, Object> config = (Map<String, Object>) configObj;
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                globalConfig.put(entry.getKey(), value.toString());
            }
        }
    }

    // ========== 接口方法实现 ==========

    /**
     * 加载单个 section 模板
     *
     * 执行流程：
     * 1. 先从缓存 templates 中查找，命中则直接返回
     * 2. 缓存未命中，从 sectionsConfig 获取该 section 的配置
     * 3. 如果 section 不存在或未启用，返回空字符串
     * 4. 如果是动态 section（file 为 null），返回空字符串
     * 5. 使用 ResourceLoader 读取对应的 YAML 文件
     * 6. 解析 YAML，提取 content 字段作为模板内容
     * 7. 存入缓存并返回
     *
     * @param sectionName section 名称，如 intro, tone_and_style
     * @return section 模板内容，如果不存在或未启用返回空字符串
     */
    @Override
    public String loadSectionTemplate(String sectionName) {
        // 1. 先从缓存查找
        if (templates.containsKey(sectionName)) {
            return templates.get(sectionName);
        }

        // 2. 从配置中获取 section 信息
        SectionConfig config = sectionsConfig.get(sectionName);
        if (config == null) {
            log.warn("[YamlPromptConfigLoader] section 不存在: {}", sectionName);
            return "";
        }

        // 3. 检查是否启用
        if (!config.enabled()) {
            log.debug("[YamlPromptConfigLoader] section 未启用: {}", sectionName);
            return "";
        }

        // 4. section 没有 file 字段（纯动态生成，无模板），返回空
        if (config.file() == null) {
            log.debug("[YamlPromptConfigLoader] section 无模板文件（动态生成）: {}", sectionName);
            return "";
        }

        // 5. 读取 section 文件
        String filePath = "classpath:prompts/" + config.file();
        try {
            Resource resource = resourceLoader.getResource(filePath);
            if (!resource.exists()) {
                log.warn("[YamlPromptConfigLoader] section 文件不存在: {}", filePath);
                return "";
            }

            // 6. 解析 YAML 提取 content 字段
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(resource.getInputStream());
            Object content = data.get("content");

            if (content == null) {
                log.warn("[YamlPromptConfigLoader] section 文件缺少 content 字段: {}", filePath);
                return "";
            }

            String template = content.toString();

            // 7. 存入缓存
            templates.put(sectionName, template);
            log.debug("[YamlPromptConfigLoader] 加载 section 模板: {} ({} 字符)", sectionName, template.length());

            return template;

        } catch (IOException e) {
            log.error("[YamlPromptConfigLoader] 读取 section 文件失败: {}", filePath, e);
            return "";
        }
    }

    /**
     * 加载所有启用的 section 模板
     *
     * 执行流程：
     * 1. 遍历 sectionsConfig 中的所有 section
     * 2. 跳过未启用的 section
     * 3. 调用 loadSectionTemplate() 加载每个模板
     * 4. 只返回非空的模板
     *
     * 返回结果示例：
     * {
     *   "intro": "你是一个智能助手...",
     *   "tone_and_style": "## 语气与风格...",
     *   "output_efficiency": "## 输出效率...",
     *   "using_your_tools": "## 工具使用指南..."
     * }
     *
     * @return Map&lt;sectionName, templateContent&gt;，只包含启用的非空模板
     */
    @Override
    public Map<String, String> loadAllSectionTemplates() {
        Map<String, String> result = new HashMap<>();

        for (String sectionName : sectionsConfig.keySet()) {
            // loadSectionTemplate 内部会检查 enabled 和 file
            String template = loadSectionTemplate(sectionName);
            if (template != null && !template.isEmpty()) {
                result.put(sectionName, template);
            }
        }

        log.debug("[YamlPromptConfigLoader] 加载所有模板，共 {} 个", result.size());
        return result;
    }

    /**
     * 检查 section 是否启用
     *
     * 执行流程：
     * 1. 从 sectionsConfig 获取该 section 的配置
     * 2. 如果 section 不存在，默认返回 true（允许扩展）
     * 3. 返回 enabled 字段的值
     *
     * 使用场景：
     * DefaultSystemPromptBuilder 构建提示词时，会先检查 section 是否启用，
     * 只拼接启用的 section，跳过禁用的 section。
     *
     * @param sectionName section 名称
     * @return true 表示启用，false 表示未启用
     */
    @Override
    public boolean isSectionEnabled(String sectionName) {
        SectionConfig config = sectionsConfig.get(sectionName);
        if (config == null) {
            // 未知的 section 默认启用，允许扩展
            log.debug("[YamlPromptConfigLoader] section 不存在，默认启用: {}", sectionName);
            return true;
        }
        return config.enabled();
    }

    /**
     * 获取全局配置值
     *
     * 执行流程：
     * 1. 从 globalConfig 中查找配置项
     * 2. 找到则包装为 Optional 返回
     * 3. 找不到返回 Optional.empty()
     *
     * 常用配置项：
     * - model_name: 模型名称，如 qwen-max
     * - knowledge_cutoff: 知识截止日期，如 2026-04
     * - separator: 静态/动态部分分隔符
     *
     * 使用场景：
     * DefaultDynamicSectionProvider 生成 env_info 时，需要获取模型名称和知识截止日期。
     *
     * @param key 配置项名称
     * @return Optional 包装的配置值，不存在则返回 empty
     */
    @Override
    public Optional<String> getConfig(String key) {
        String value = globalConfig.get(key);
        if (value == null) {
            log.debug("[YamlPromptConfigLoader] 配置项不存在: {}", key);
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
