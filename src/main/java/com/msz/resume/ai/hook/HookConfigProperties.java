package com.msz.resume.ai.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Hook 配置加载器
 *
 * <p>从 {@code resources/hooks/tool-hooks.yml} 加载 Hook 规则，
 * 解析为 {@link HookRule} 列表，供 {@link HookEngine} 使用。
 *
 * <h2>配置结构</h2>
 * <pre>
 * pre_tool_use:
 *   - name: sub_agent_block
 *     matcher: "^(askUserQuestion|askMultipleQuestions)$"
 *     action: subAgentBlockHook
 *     priority: 10
 * post_tool_use:
 *   - name: audit_log
 *     matcher: ".*"
 *     action: auditLogHook
 *     priority: 100
 * </pre>
 *
 * <h2>热加载</h2>
 * 调用 {@link #reload()} 重新读取 YAML 文件，不影响正在执行的 Hook。
 * 加载失败时保持上一次的有效配置。
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "jarvis.hooks")
public class HookConfigProperties {

    private static final String HOOKS_CONFIG_PATH = "classpath:hooks/tool-hooks.yml";

    /** PreToolUse 规则列表 */
    private volatile List<HookRule> preToolUseRules = Collections.emptyList();

    /** PostToolUse 规则列表 */
    private volatile List<HookRule> postToolUseRules = Collections.emptyList();

    private final ResourceLoader resourceLoader;

    public HookConfigProperties(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        reload();
    }

    /**
     * 热加载：重新读取 YAML 配置文件
     *
     * <p>加载失败时保持现有配置不变，不中断服务。
     */
    public void reload() {
        try {
            Resource resource = resourceLoader.getResource(HOOKS_CONFIG_PATH);
            if (!resource.exists()) {
                log.info("[HookConfig] 配置文件不存在: {}，使用空规则集", HOOKS_CONFIG_PATH);
                return;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(resource.getInputStream());

            List<HookRule> preRules = parseRules(data, "pre_tool_use");
            List<HookRule> postRules = parseRules(data, "post_tool_use");

            // 原子替换：读取端使用 volatile，保证可见性
            this.preToolUseRules = List.copyOf(preRules);
            this.postToolUseRules = List.copyOf(postRules);

            log.info("[HookConfig] 配置加载完成：pre={}, post={}",
                    preRules.size(), postRules.size());

        } catch (IOException e) {
            log.error("[HookConfig] 加载配置文件失败，保持现有配置", e);
        } catch (Exception e) {
            log.error("[HookConfig] 解析配置文件失败，保持现有配置", e);
        }
    }

    /**
     * 解析指定段的规则列表
     */
    @SuppressWarnings("unchecked")
    private List<HookRule> parseRules(Map<String, Object> data, String sectionKey) {
        Object sectionObj = data.get(sectionKey);
        if (!(sectionObj instanceof List)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) sectionObj;
        List<HookRule> rules = new ArrayList<>();

        for (Map<String, Object> ruleMap : ruleMaps) {
            try {
                String name = (String) ruleMap.get("name");
                String matcher = (String) ruleMap.get("matcher");
                String action = (String) ruleMap.get("action");
                int priority = ruleMap.get("priority") instanceof Number n ? n.intValue() : 100;

                if (name == null || matcher == null || action == null) {
                    log.warn("[HookConfig] 规则缺少必要字段(name/matcher/action)，跳过: {}", ruleMap);
                    continue;
                }

                rules.add(new HookRule(name, matcher, action, priority));

            } catch (Exception e) {
                log.warn("[HookConfig] 解析规则失败，跳过: {}", ruleMap, e);
            }
        }

        return rules;
    }

    public List<HookRule> getPreToolUseRules() {
        return preToolUseRules;
    }

    public List<HookRule> getPostToolUseRules() {
        return postToolUseRules;
    }
}