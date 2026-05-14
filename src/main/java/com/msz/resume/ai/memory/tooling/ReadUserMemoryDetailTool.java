package com.msz.resume.ai.memory.tooling;

import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingUserMemoryService;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 读取用户长期 Markdown 记忆 L2 明细工具（延迟工具）。
 */
@Slf4j
@CoreTool
@Component
@RequiredArgsConstructor
public class ReadUserMemoryDetailTool {

    private final OpenVikingUserMemoryService openVikingUserMemoryService;

    /**
     * 按文件名读取 OpenViking 用户记忆 L2 原文。
     *
     * @param userId 当前用户 ID，必须来自会话中的 UserProfile
     * @param filename 记忆文件名，格式为 {type}_{key}.md
     * @param memoryId filename 的兼容别名
     * @return 记忆内容或失败说明
     */
    @Tool("Read one L2 user memory detail file from OpenViking. Use only after you have already identified a plausible target file from memory overview, directory browsing, grep, glob, or another retrieval step, and only when exact memory content is needed. Provide filename in the format {type}_{key}.md, for example user_favorite_singers.md. If an older caller provides memoryId, it is treated as the same value as filename.")
    public String readUserMemoryDetail(
            @P("Current user id from user context. Do not invent it. If unavailable, do not call this tool.") String userId,
            @P("Memory filename in the format {type}_{key}.md, for example user_favorite_singers.md. Preferred parameter.") String filename,
            @P(value = "Backward-compatible alias for filename. Use only if filename is unavailable.", required = false) String memoryId) {

        log.info("[ReadUserMemoryDetailTool] 读取用户长期记忆 L2 明细, userId={}, filename={}, memoryId={}",
                userId, filename, memoryId);

        if (!hasText(userId)) {
            return "读取长期记忆失败：userId 为空。";
        }

        String effectiveFilename = hasText(filename) ? filename.trim() : (hasText(memoryId) ? memoryId.trim() : null);
        if (!hasText(effectiveFilename)) {
            return "读取长期记忆失败：filename 为空。";
        }

        try {
            String content = openVikingUserMemoryService.readMemoryDetail(userId.trim(), effectiveFilename);
            if (!hasText(content)) {
                return "读取长期记忆失败：文件内容为空。";
            }
            return "已读取长期记忆明细。\n"
                    + "file=" + effectiveFilename + "\n"
                    + "content=\n" + content;
        } catch (Exception e) {
            log.warn("[ReadUserMemoryDetailTool] 读取用户长期记忆 L2 明细失败, userId={}, filename={}, message={}",
                    userId, effectiveFilename, e.getMessage());
            return "读取长期记忆失败：" + e.getMessage();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
