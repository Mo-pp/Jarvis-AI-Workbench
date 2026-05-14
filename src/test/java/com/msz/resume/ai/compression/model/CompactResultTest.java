package com.msz.resume.ai.chat.compression.model;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompactResult 单元测试
 */
class CompactResultTest {

    @Test
    @DisplayName("unchanged() 创建未改变的结果")
    void unchanged_shouldCreateResultWithZeroCount() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        CompactResult result = CompactResult.unchanged(messages);

        assertEquals(messages, result.messages());
        assertEquals(0, result.compactedCount());
        assertEquals(0, result.tokensSaved());
        assertTrue(result.compactedToolNames().isEmpty());
        assertFalse(result.wasCompacted());
    }

    @Test
    @DisplayName("wasCompacted() 清理数量>0时返回true")
    void wasCompacted_whenCountGreaterThanZero_shouldReturnTrue() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        CompactResult result = new CompactResult(messages, 3, 500, List.of("fileRead", "grep"));

        assertTrue(result.wasCompacted());
    }
}
