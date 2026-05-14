package com.msz.resume.ai.chat.prompt.config;

import java.util.Map;
import java.util.Optional;

/**
 * 系统提示词配置加载器接口
 */
public interface PromptConfigLoader {

    /**
     * 加载单个section模板
     */
    String loadSectionTemplate(String sectionName);

    /**
     * 加载所有section模板
     */
    Map<String, String> loadAllSectionTemplates();

    /**
     * 检查section是否启用
     */
    boolean isSectionEnabled(String sectionName);

    /**
     * 获取配置值
     */
    Optional<String> getConfig(String key);

    /**
     * 热更新配置
     */
    void reload();

    /**
     * 获取模型名称
     *
     * @return 模型名称，如 qwen-max
     */
    default String getModelName() {
        return getConfig("model_name").orElse("qwen-max");
    }

    /**
     * 获取知识截止日期
     *
     * @return 知识截止日期，如 2026-04
     */
    default String getKnowledgeCutoff() {
        return getConfig("knowledge_cutoff").orElse("2024-04");
    }
}
