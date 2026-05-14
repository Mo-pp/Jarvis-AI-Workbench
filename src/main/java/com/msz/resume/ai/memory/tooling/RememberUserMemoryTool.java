package com.msz.resume.ai.memory.tooling;

import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingUserMemoryService;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户长期 Markdown 记忆工具（延迟工具）。
 *
 * <p>只在用户明确要求“记住、以后、默认、长期偏好”等未来持续生效的信息时使用。</p>
 */
@Slf4j
@CoreTool
@Component
@RequiredArgsConstructor
public class RememberUserMemoryTool {

    private final OpenVikingUserMemoryService openVikingUserMemoryService;

    /**
     * 把用户明确要求长期记住的信息保存为 OpenViking Markdown 记忆。
     *
     * @param userId 当前用户 ID，必须来自会话中的 UserProfile
     * @param type 记忆类型：user、feedback、project、reference
     * @param key 稳定语义 key，用于生成文件名
     * @param name 简短记忆名称
     * @param description L2 frontmatter 描述，供 OpenViking 后续生成 L0/L1 使用
     * @param content 记忆正文
     * @return 写入结果说明，供 LLM 回复用户
     */
    @Tool("Remember a user's explicit long-term memory into OpenViking as a Markdown memory file. Use only when the user explicitly asks to remember something for future conversations, or states a durable preference, fact, workflow rule, project constraint, or reference that should persist. Supported type values: user = user personal facts, long-term preferences, background, stable profile information; feedback = user's instructions about how the assistant should work, approval rules, workflow preferences, tool or command execution rules, corrections about assistant behavior; project = project background, current goals, durable constraints, stakeholder context; reference = external systems, documents, dashboards, tickets, or places to look up current information. Provide a stable semantic key in lowercase English words such as favorite_adult_actresses or language_and_workflow; description is only for retrieval and must not be used as identity. Do not use for temporary one-off requests. If a narrower memory tool does not apply, switch here instead of stopping.")
    public String rememberUserMemory(
            @P("Current user id from user context. Do not invent it. If unavailable, do not call this tool.") String userId,
            @P("Memory type. Must be one of: user, feedback, project, reference.") String type,
            @P("Stable semantic key used for the memory filename, for example favorite_adult_actresses or language_and_workflow. Keep it stable when updating the same memory.") String key,
            @P("Short human-readable memory name, for example 工作流偏好 or 喜欢的av女优.") String name,
            @P("One-line description stored in the L2 memory frontmatter for OpenViking L0/L1 generation. Changing this must not change the memory identity.") String description,
            @P("Memory content in the user's natural language. Should contain the durable fact, rule, context, or reference to apply in future conversations.") String content) {

        log.info("[RememberUserMemoryTool] 记住用户长期 Markdown 记忆, userId={}, type={}, key={}, name={}",
                userId, type, key, name);

        if (!hasText(userId)) {
            return "保存长期记忆失败：userId 为空。";
        }
        if (!hasText(type)) {
            return "保存长期记忆失败：type 为空。";
        }
        if (!hasText(key)) {
            return "保存长期记忆失败：key 为空。";
        }
        if (!hasText(name)) {
            return "保存长期记忆失败：name 为空。";
        }
        if (!hasText(description)) {
            return "保存长期记忆失败：description 为空。";
        }
        if (!hasText(content)) {
            return "保存长期记忆失败：content 为空。";
        }

        try {
            OpenVikingUserMemoryService.SaveMemoryResult result = openVikingUserMemoryService.saveMemory(
                    userId.trim(),
                    type.trim(),
                    key.trim(),
                    name.trim(),
                    description.trim(),
                    content.trim()
            );
            String output = "已保存长期记忆。\n"
                    + "type=" + result.type() + "\n"
                    + "file=" + result.filename() + "\n"
                    + "uri=" + result.fileUri() + "\n"
                    + "status=" + result.status() + "\n"
                    + "memory_saved=" + result.memorySaved() + "\n"
                    + "semantic_refresh=async\n"
                    + "read_strategy=" + result.readStrategy();
            return output;
        } catch (Exception e) {
            log.warn("[RememberUserMemoryTool] 保存用户长期 Markdown 记忆失败, userId={}, type={}, key={}, name={}, message={}",
                    userId, type, key, name, e.getMessage());
            return "status=failed\n"
                    + "memory_saved=false\n"
                    + "memory_effective=false\n"
                    + "message=保存长期记忆失败：" + e.getMessage() + "\n"
                    + "required_user_reply=长期记忆保存失败，未生效，请稍后重试。\n"
                    + "forbidden_claims=禁止声称“已经记住”“已保存”“以后会遵守”“已生效”。\n"
                    + "assistant_instruction=你必须明确告诉用户保存失败；不得把本次内容当作长期记忆；不得承诺未来会记住。";
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
