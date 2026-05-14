package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.CompactResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认微压缩器实现
 *
 * <p>实现规则：
 * <ul>
 *   <li>保留最近 keepRecent 个可压缩工具结果的完整内容</li>
 *   <li>更早的结果替换为 [旧工具结果已清理]</li>
 *   <li>消息数量、顺序、结构保持不变</li>
 *   <li>非可压缩工具结果原样保留</li>
 * </ul>
 */
@Slf4j
@Component
public class DefaultMicrocompact implements Microcompact {

    /** 可压缩工具类型（参考 Claude Code 列表） */
    private static final Set<String> COMPACTABLE_TOOLS = Set.of(
            "fileRead",      // 文件读取
            "shell",         // Shell 命令
            "grep",          // 内容搜索
            "glob",          // 文件搜索
            "webSearch",     // Web 搜索
            "webFetch",      // Web 抓取
            "fileEdit",      // 文件编辑
            "fileWrite"      // 文件写入
    );

    private final JarvisCompressionProperties properties;

    public DefaultMicrocompact(JarvisCompressionProperties properties) {
        this.properties = properties;
    }

    @Override
    public CompactResult compact(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return CompactResult.unchanged(messages);
        }

        // 1. 找出所有可压缩工具结果的索引
        List<Integer> compactableIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ToolExecutionResultMessage toolMsg) {
                // 从消息中提取工具名称
                // ToolExecutionResultMessage 的 id 格式为 toolCallId，需要从上下文获取工具名称
                // 暂时使用简单判断：检查内容是否来自可压缩工具
                // 实际实现需要在消息中携带工具名称元数据
                if (isCompactableToolResult(toolMsg)) {
                    compactableIndices.add(i);
                }
            }
        }

        // 2. 判断是否需要清理
        int keepRecent = properties.getKeepRecent();
        if (compactableIndices.size() <= keepRecent) {
            // 不需要清理
            return CompactResult.unchanged(messages);
        }

        // 3. 计算需要清理的索引（保留最近 keepRecent 个）
        int toCompactCount = compactableIndices.size() - keepRecent;
        Set<Integer> indicesToCompact = new HashSet<>(
                compactableIndices.subList(0, toCompactCount)
        );

        // 4. 重建消息列表
        List<ChatMessage> newMessages = new ArrayList<>();
        int compactedCount = 0;
        int tokensSaved = 0;
        List<String> compactedToolNames = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (indicesToCompact.contains(i)) {
                // 需要清理
                ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) msg;
                String originalContent = toolMsg.text();
                int originalTokens = estimateTokens(originalContent);

                // 创建清理后的消息：使用 from(id, toolName, text) 构造方法
                ToolExecutionResultMessage cleanedMsg = ToolExecutionResultMessage.from(
                        toolMsg.id(),
                        toolMsg.toolName(),
                        CLEARED_MESSAGE
                );
                newMessages.add(cleanedMsg);

                compactedCount++;
                tokensSaved += originalTokens - estimateTokens(CLEARED_MESSAGE);
                compactedToolNames.add(extractToolName(toolMsg));
            } else {
                // 原样保留
                newMessages.add(msg);
            }
        }

        log.info("[Microcompact] 清理了 {} 个工具结果，节省约 {} tokens", compactedCount, tokensSaved);

        return new CompactResult(newMessages, compactedCount, tokensSaved, compactedToolNames);
    }

    @Override
    public Set<String> getCompactableTools() {
        return Collections.unmodifiableSet(COMPACTABLE_TOOLS);
    }

    @Override
    public boolean isCompactable(String toolName) {
        return COMPACTABLE_TOOLS.contains(toolName);
    }

    /**
     * 判断工具结果是否来自可压缩工具
     *
     * @param toolMsg 工具执行结果消息
     * @return 是否来自可压缩工具
     */
    private boolean isCompactableToolResult(ToolExecutionResultMessage toolMsg) {
        String toolName = toolMsg.toolName();
        return toolName != null && COMPACTABLE_TOOLS.contains(toolName);
    }

    /**
     * 从工具结果消息中提取工具名称
     *
     * @param toolMsg 工具执行结果消息
     * @return 工具名称或 "unknown"
     */
    private String extractToolName(ToolExecutionResultMessage toolMsg) {
        String toolName = toolMsg.toolName();
        return toolName != null ? toolName : "unknown";
    }

    /**
     * 估算文本的 Token 数
     *
     * <p>使用简单的字符数 / 4 估算（中文约 1.5 字符/token，英文约 4 字符/token）
     *
     * @param text 文本内容
     * @return 估算的 Token 数
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 使用保守估算：字符数 / 3
        return text.length() / 3;
    }
}
