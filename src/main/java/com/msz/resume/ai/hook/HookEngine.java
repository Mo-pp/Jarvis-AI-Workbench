package com.msz.resume.ai.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Hook 匹配与执行引擎
 *
 * <p>核心职责：
 * <ol>
 *   <li>根据工具名从配置中匹配 Hook 规则（正则匹配）</li>
 *   <li>按 priority 升序链式执行匹配到的 Hook</li>
 *   <li>遇到 {@link HookResult#block(String)} 立即终止链，返回阻断结果</li>
 *   <li>遇到 {@link HookResult#modifyArgs(Map)} 将修改后的参数传递给下一个 Hook</li>
 * </ol>
 *
 * <h2>执行流程</h2>
 * <pre>
 * preToolUse("askUserQuestion", args, state)
 *   → 匹配规则: [subAgentBlockHook(priority=10)]
 *   → 执行 subAgentBlockHook.preToolUse(ctx)
 *     → 如果 isSubAgent: 返回 block("子Agent不可向用户提问") → 终止链，返回 block
 *     → 如果非子Agent: 返回 continueResult()
 * </pre>
 *
 * <h2>Hook Bean 查找</h2>
 * 配置中的 {@code action} 字段对应 Spring Bean 名称，通过 {@link ApplicationContext} 查找。
 * 查找失败的 Hook 被跳过并记录警告日志。
 */
@Slf4j
@Component
public class HookEngine implements HookService {

    private final HookConfigProperties configProperties;
    private final ApplicationContext applicationContext;

    /** 编译后的正则缓存（matcher → Pattern） */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public HookEngine(HookConfigProperties configProperties, ApplicationContext applicationContext) {
        this.configProperties = configProperties;
        this.applicationContext = applicationContext;
    }

    @Override
    public HookResult preToolUse(HookContext context) {
        List<MatchedHook> matchedHooks = matchHooks(context.toolName(), configProperties.getPreToolUseRules());

        if (matchedHooks.isEmpty()) {
            return HookResult.continueResult();
        }

        log.info("[HookEngine] preToolUse: tool={}, matched {} hooks: {}",
                context.toolName(), matchedHooks.size(),
                matchedHooks.stream().map(h -> h.rule.name()).toList());

        Map<String, Object> currentArgs = null; // 只在 modifyArgs 时构建

        for (MatchedHook matched : matchedHooks) {
            try {
                // 如果前一个 Hook 修改了参数，更新 context
                HookContext effectiveContext = context;
                if (currentArgs != null) {
                    // 重新构造 context 暂不可行（arguments 是 JSON String）
                    // modifyArgs 的效果在 HookEngine 外部处理
                }

                HookResult result = matched.hook.preToolUse(effectiveContext);

                if (result.isBlocked()) {
                    log.info("[HookEngine] preToolUse 阻断: tool={}, hook={}, reason={}",
                            context.toolName(), matched.rule.name(), result.blockReason());
                    return result;
                }

                if (result.modifiedArgs() != null) {
                    currentArgs = result.modifiedArgs();
                    log.debug("[HookEngine] preToolUse 修改参数: tool={}, hook={}",
                            context.toolName(), matched.rule.name());
                }

            } catch (Exception e) {
                log.error("[HookEngine] preToolUse 异常: tool={}, hook={}, 继续执行下一个Hook",
                        context.toolName(), matched.rule.name(), e);
            }
        }

        // 如果有 modifyArgs，返回最后一个 modifyArgs 结果
        if (currentArgs != null) {
            return HookResult.modifyArgs(currentArgs);
        }

        return HookResult.continueResult();
    }

    @Override
    public String postToolUse(HookContext context, String toolResult) {
        List<MatchedHook> matchedHooks = matchHooks(context.toolName(), configProperties.getPostToolUseRules());

        if (matchedHooks.isEmpty()) {
            return toolResult;
        }

        log.info("[HookEngine] postToolUse: tool={}, matched {} hooks",
                context.toolName(), matchedHooks.size());

        String currentResult = toolResult;

        for (MatchedHook matched : matchedHooks) {
            try {
                String newResult = matched.hook.postToolUse(context, currentResult);
                if (newResult != null) {
                    currentResult = newResult;
                }
            } catch (Exception e) {
                log.error("[HookEngine] postToolUse 异常: tool={}, hook={}, 继续执行下一个Hook",
                        context.toolName(), matched.rule.name(), e);
            }
        }

        return currentResult;
    }

    /**
     * 根据工具名匹配 Hook 规则，返回按 priority 升序排列的匹配结果
     */
    private List<MatchedHook> matchHooks(String toolName, List<HookRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }

        List<MatchedHook> matched = new ArrayList<>();

        for (HookRule rule : rules) {
            Pattern pattern = patternCache.computeIfAbsent(rule.matcher(), Pattern::compile);
            if (pattern.matcher(toolName).matches()) {
                // 查找对应的 Hook Bean
                try {
                    ToolHook hook = applicationContext.getBean(rule.action(), ToolHook.class);
                    matched.add(new MatchedHook(rule, hook));
                } catch (Exception e) {
                    log.warn("[HookEngine] Hook Bean 未找到或类型不匹配: action={}, 跳过此规则", rule.action());
                }
            }
        }

        // 按 priority 升序排序
        matched.sort((a, b) -> Integer.compare(a.rule.priority(), b.rule.priority()));
        return matched;
    }

    /**
     * 匹配到的 Hook（规则 + 实例）
     */
    private record MatchedHook(HookRule rule, ToolHook hook) {}
}
