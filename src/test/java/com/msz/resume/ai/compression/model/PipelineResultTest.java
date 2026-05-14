package com.msz.resume.ai.chat.compression.model;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PipelineResult 单元测试
 */
class PipelineResultTest {

    @Test
    @DisplayName("unchanged() 创建未改变的结果")
    void unchanged_shouldCreateResultWithWasCompressedFalse() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        PipelineResult result = PipelineResult.unchanged(messages, 100);

        assertEquals(messages, result.messages());
        assertFalse(result.wasCompressed());
        assertEquals(100, result.originalTokens());
        assertEquals(100, result.finalTokens());
        assertTrue(result.executedLevels().isEmpty());
    }

    @Test
    @DisplayName("tokensSaved() 正确计算节省Token数")
    void tokensSaved_shouldCalculateCorrectly() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        PipelineResult result = new PipelineResult(messages, true, 1000, 600, List.of("L3"));

        assertEquals(400, result.tokensSaved());
    }

    @Test
    @DisplayName("压缩结果包含执行的层级列表")
    void compressedResult_shouldContainExecutedLevels() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        PipelineResult result = new PipelineResult(messages, true, 1000, 600, List.of("L3"));

        assertEquals(List.of("L3"), result.executedLevels());
    }
}
