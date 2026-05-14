package com.msz.resume.ai.chat.prompt.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * 系统提示词配置加载器注册
 *
 * 业务背景：
 * YamlPromptConfigLoader 需要注入 ResourceLoader 来读取 classpath 下的配置文件，
 * 通过配置类注册为 Spring Bean，便于管理和测试。
 *
 * 使用场景：
 * 其他组件（如 DefaultSystemPromptBuilder、DefaultDynamicSectionProvider）
 * 通过依赖注入获取 PromptConfigLoader 接口实现。
 */
@Configuration
@RequiredArgsConstructor
public class PromptConfigLoaderConfig {

    private final ResourceLoader resourceLoader;

    /**
     * 注册 PromptConfigLoader Bean
     *
     * 返回接口类型 PromptConfigLoader，便于后续切换实现
     * （如从 YAML 切换到数据库配置）
     */
    @Bean
    public PromptConfigLoader promptConfigLoader() {
        return new YamlPromptConfigLoader(resourceLoader);
    }
}
