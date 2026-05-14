package com.msz.resume.ai.memory.tooling;

import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingUserMemoryService;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 读取 OpenViking 用户长期记忆工具（延迟工具）。
 */
@Slf4j
@CoreTool
@Component
@RequiredArgsConstructor
public class ReadUserMemoryTool {

    private static final String LEVEL_OVERVIEW = "overview";
    private static final String LEVEL_DETAIL = "detail";

    private final OpenVikingUserMemoryService openVikingUserMemoryService;

    /**
     * 按 OpenViking L0/L1/L2 渐进式披露读取用户长期记忆。
     *
     * @param userId 当前用户 ID，必须来自会话中的 UserProfile
     * @param level  读取层级：overview 或 detail
     * @param filename detail 层级要读取的记忆文件名，格式为 {type}_{key}.md
     * @return 记忆内容或失败说明
     */
    @Tool("Read OpenViking user memory progressively. Default system context already includes L0 abstract. Use level=overview to read L1 .overview.md when memory summary is specifically needed. Use level=detail with filename to read one L2 memory file only when exact memory content is needed. If overview is empty, abnormal, wrapped, or insufficient, do not conclude that memory is absent; switch to openviking_list, openviking_tree, openviking_glob, or openviking_grep on viking://user/{userId}/memories/ to locate candidate files first.")
    public String readUserMemory(
            @P("Current user id from user context. Do not invent it. If unavailable, do not call this tool.") String userId,
            @P("Memory level to read: overview or detail.") String level,
            @P(value = "Required only when level=detail. Memory filename in the format {type}_{key}.md, for example user_favorite_singers.md.", required = false) String filename) {

        log.info("[ReadUserMemoryTool] 读取用户长期记忆, userId={}, level={}, filename={}", userId, level, filename);

        if (!hasText(userId)) {
            return "读取长期记忆失败：userId 为空。";
        }
        if (!hasText(level)) {
            return "读取长期记忆失败：level 为空。";
        }

        String normalizedLevel = level.trim().toLowerCase();
        try {
            if (LEVEL_OVERVIEW.equals(normalizedLevel)) {
                String content = openVikingUserMemoryService.readMemoryOverview(userId.trim());
                if (!hasText(content)) {
                    return "读取长期记忆失败：overview 内容为空。";
                }
                return "已读取长期记忆概览。\n"
                        + "level=overview\n"
                        + "file=.overview.md\n"
                        + "content=\n" + content;
            }

            if (LEVEL_DETAIL.equals(normalizedLevel)) {
                if (!hasText(filename)) {
                    return "读取长期记忆失败：level=detail 时 filename 必填。";
                }
                String effectiveFilename = filename.trim();
                String content = openVikingUserMemoryService.readMemoryDetail(userId.trim(), effectiveFilename);
                if (!hasText(content)) {
                    return "读取长期记忆失败：文件内容为空。";
                }
                return "已读取长期记忆明细。\n"
                        + "level=detail\n"
                        + "file=" + effectiveFilename + "\n"
                        + "content=\n" + content;
            }

            return "读取长期记忆失败：level 必须是 overview 或 detail。";
        } catch (Exception e) {
            log.warn("[ReadUserMemoryTool] 读取用户长期记忆失败, userId={}, level={}, filename={}, message={}",
                    userId, level, filename, e.getMessage());
            return "读取长期记忆失败：" + e.getMessage();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
