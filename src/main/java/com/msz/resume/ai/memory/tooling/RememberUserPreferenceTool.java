package com.msz.resume.ai.memory.tooling;

import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteResponse;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingMemoryService;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户长期偏好记忆工具（延迟工具）。
 *
 * <p>业务场景：</p>
 * <ul>
 *     <li>用户明确要求“以后默认用中文/英文回复我”</li>
 *     <li>用户明确要求“记住我的回答风格偏好”</li>
 *     <li>用户表达的是未来持续生效的偏好，而不是本轮临时要求</li>
 * </ul>
 *
 * <p>第一版刻意只允许写入 language-style 这一类偏好，避免 LLM 任意写 OpenViking URI
 * 或把未设计好的记忆结构写乱。真实落盘路径由 {@link OpenVikingMemoryService} 统一控制：</p>
 *
 * <pre>
 * viking://user/{userId}/memories/preferences/language-style.md
 * </pre>
 */
@Slf4j
@CoreTool
@Component
@RequiredArgsConstructor
public class RememberUserPreferenceTool {

    private static final String SUPPORTED_KEY_LANGUAGE_STYLE = "language-style";

    private final OpenVikingMemoryService openVikingMemoryService;

    /**
     * 记住用户明确表达的长期偏好。
     *
     * <p>此工具只应该在用户明确表达“以后、默认、记住、长期偏好”等意图时使用。
     * 如果用户只是本轮要求“这次用中文/简单说”，不应该调用此工具。</p>
     *
     * @param userId 当前用户 ID，必须来自会话中的 UserProfile
     * @param key 偏好键名；第一版只支持 language-style
     * @param content 需要长期保存的偏好内容
     * @return 写入结果说明，供 LLM 回复用户
     */
    @Tool("Remember only a user's long-term language or response style preference into OpenViking. Use this only for requests like 'always reply in Chinese', 'default to concise answers', or 'remember my response style'. First version supports only key=language-style. Do not use this for workflow rules, code modification process, approval rules, behavior corrections, personal facts, or project facts; those are not language-style preferences. If the request is durable but not language-style, switch to rememberUserMemory instead of stopping.")
    public String rememberUserPreference(
            @P("Current user id from user context. Do not invent it. If unavailable, do not call this tool.") String userId,
            @P("Preference key. First version supports only language-style.") String key,
            @P("Preference content to remember, written in the user's natural language.") String content) {

        log.info("[RememberUserPreferenceTool] 记住用户偏好, userId={}, key={}", userId, key);

        if (!hasText(userId)) {
            return "记住用户偏好失败：userId 为空。";
        }
        if (!hasText(key)) {
            return "记住用户偏好失败：key 为空。";
        }
        if (!SUPPORTED_KEY_LANGUAGE_STYLE.equals(key.trim())) {
            return "记住用户偏好失败：该工具只支持 language-style；行为规则、工作流规则、审批规则或代码修改流程请使用 rememberUserMemory。";
        }
        if (!hasText(content)) {
            return "记住用户偏好失败：content 为空。";
        }

        try {
            OpenVikingWriteResponse response = openVikingMemoryService.writePreference(
                    userId.trim(),
                    SUPPORTED_KEY_LANGUAGE_STYLE,
                    content.trim()
            );
            String uri = "viking://user/" + userId.trim() + "/memories/preferences/" + SUPPORTED_KEY_LANGUAGE_STYLE + ".md";
            String status = response != null ? response.status() : "unknown";
            return "已记住用户偏好。\nkey=language-style\nuri=" + uri + "\nstatus=" + status;
        } catch (Exception e) {
            log.warn("[RememberUserPreferenceTool] 记住用户偏好失败, userId={}, key={}, message={}",
                    userId, key, e.getMessage());
            return "记住用户偏好失败：" + e.getMessage();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
