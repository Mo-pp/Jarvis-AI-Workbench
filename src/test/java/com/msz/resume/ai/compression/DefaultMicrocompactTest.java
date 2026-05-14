package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.CompactResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultMicrocompact 单元测试
 */
class DefaultMicrocompactTest {

    private JarvisCompressionProperties properties;
    private DefaultMicrocompact microcompact;

    @BeforeEach
    void setUp() {
        properties = new JarvisCompressionProperties();
        properties.setKeepRecent(2);  // 保留最近 2 个
        microcompact = new DefaultMicrocompact(properties);
    }

    @Test
    @DisplayName("getCompactableTools() 返回不可修改的工具集合")
    void getCompactableTools_shouldReturnUnmodifiableSet() {
        var tools = microcompact.getCompactableTools();

        assertTrue(tools.contains("fileRead"));
        assertTrue(tools.contains("shell"));
        assertTrue(tools.contains("grep"));
        assertThrows(UnsupportedOperationException.class, () -> tools.add("newTool"));
    }

    @Test
    @DisplayName("isCompactable() 正确判断工具是否可压缩")
    void isCompactable_shouldReturnCorrectResult() {
        assertTrue(microcompact.isCompactable("fileRead"));
        assertTrue(microcompact.isCompactable("shell"));
        assertTrue(microcompact.isCompactable("grep"));
        assertFalse(microcompact.isCompactable("toolSearch"));
        assertFalse(microcompact.isCompactable("unknownTool"));
    }

    @Test
    @DisplayName("compact() 空消息列表返回不变")
    void compact_whenEmptyMessages_shouldReturnUnchanged() {
        List<ChatMessage> messages = new ArrayList<>();

        CompactResult result = microcompact.compact(messages);

        assertFalse(result.wasCompacted());
        assertEquals(0, result.compactedCount());
    }

    @Test
    @DisplayName("compact() 消息数量不超过 keepRecent 不清理")
    void compact_whenMessagesBelowThreshold_shouldNotCompact() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello"));
        messages.add(createToolResult("call-1", "fileRead", "File content 1"));
        messages.add(createToolResult("call-2", "shell", "Command output"));

        CompactResult result = microcompact.compact(messages);

        // 只有 2 个可压缩结果，keepRecent=2，不需要清理
        assertFalse(result.wasCompacted());
        assertEquals(0, result.compactedCount());
    }

    @Test
    @DisplayName("compact() 超过阈值时清理旧结果")
    void compact_whenExceedsThreshold_shouldCompactOldResults() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello"));
        messages.add(createToolResult("call-1", "fileRead", "Large file content 1..."));
        messages.add(createToolResult("call-2", "shell", "Large command output 2..."));
        messages.add(createToolResult("call-3", "fileRead", "Large file content 3..."));
        messages.add(createToolResult("call-4", "grep", "Large search results 4..."));

        CompactResult result = microcompact.compact(messages);

        // 4 个可压缩结果，keepRecent=2，应该清理前 2 个
        assertTrue(result.wasCompacted());
        assertEquals(2, result.compactedCount());
        assertEquals(5, result.messages().size()); // 消息数量不变

        // 检查被清理的消息内容
        ToolExecutionResultMessage cleaned1 = (ToolExecutionResultMessage) result.messages().get(1);
        ToolExecutionResultMessage cleaned2 = (ToolExecutionResultMessage) result.messages().get(2);
        assertEquals(Microcompact.CLEARED_MESSAGE, cleaned1.text());
        assertEquals(Microcompact.CLEARED_MESSAGE, cleaned2.text());

        // 检查保留的消息内容
        ToolExecutionResultMessage kept1 = (ToolExecutionResultMessage) result.messages().get(3);
        ToolExecutionResultMessage kept2 = (ToolExecutionResultMessage) result.messages().get(4);
        assertEquals("Large file content 3...", kept1.text());
        assertEquals("Large search results 4...", kept2.text());
    }

    @Test
    @DisplayName("compact() 非可压缩工具结果不清理")
    void compact_shouldNotCompactNonCompactableTools() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(createToolResult("call-1", "toolSearch", "Search result 1"));
        messages.add(createToolResult("call-2", "toolSearch", "Search result 2"));
        messages.add(createToolResult("call-3", "toolSearch", "Search result 3"));

        CompactResult result = microcompact.compact(messages);

        // toolSearch 不是可压缩工具，不应清理
        assertFalse(result.wasCompacted());
        assertEquals(0, result.compactedCount());
    }

    @Test
    @DisplayName("compact() 混合消息只清理可压缩工具")
    void compact_shouldOnlyCompactCompactableTools() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("Hello"));
        messages.add(createToolResult("call-1", "fileRead", "File content 1"));
        messages.add(createToolResult("call-2", "toolSearch", "Search result")); // 非可压缩
        messages.add(createToolResult("call-3", "shell", "Command output"));
        messages.add(createToolResult("call-4", "fileRead", "File content 4"));
        messages.add(UserMessage.from("Another message"));

        CompactResult result = microcompact.compact(messages);

        // 有 3 个可压缩结果，keepRecent=2，清理 1 个
        assertTrue(result.wasCompacted());
        assertEquals(1, result.compactedCount());

        // 检查非可压缩工具结果不变
        ToolExecutionResultMessage searchResult = (ToolExecutionResultMessage) result.messages().get(2);
        assertEquals("Search result", searchResult.text());
    }

    @Test
    @DisplayName("compact() 清理后节省 token 数大于 0")
    void compact_shouldSaveTokens() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(createToolResult("call-1", "fileRead", "A".repeat(1000)));
        messages.add(createToolResult("call-2", "shell", "B".repeat(1000)));
        messages.add(createToolResult("call-3", "fileRead", "C".repeat(1000)));

        CompactResult result = microcompact.compact(messages);

        assertTrue(result.wasCompacted());
        assertTrue(result.tokensSaved() > 0);
    }

    // ==================== 测试辅助方法 ====================

    private ToolExecutionResultMessage createToolResult(String id, String toolName, String text) {
        return ToolExecutionResultMessage.from(id, toolName, text);
    }
}
