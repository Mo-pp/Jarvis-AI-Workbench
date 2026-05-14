package com.msz.resume.ai.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MindmapResponseExtractorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MINDMAP_JSON = "{\"type\":\"mindmap\",\"markdown\":\"# 学习路线\\n- RAG\\n- Agent\"}";

    @Test
    @DisplayName("只提取 generateMindmap 工具产生的思维导图")
    void extractLatestMindmapDataOnlyFromMindmapTool() {
        List<ChatMessage> messages = List.of(
                ToolExecutionResultMessage.from("call-1", "someOtherTool", MINDMAP_JSON),
                ToolExecutionResultMessage.from("call-2", "generateMindmap", MINDMAP_JSON)
        );

        String result = MindmapResponseExtractor.extractLatestMindmapData(messages, OBJECT_MAPPER);

        assertEquals(MINDMAP_JSON, result);
    }

    @Test
    @DisplayName("普通 AI Markdown 回答不会被提取为思维导图")
    void extractLatestMindmapDataIgnoresAssistantMarkdown() {
        List<ChatMessage> messages = List.of(
                AiMessage.from("## 学习计划总览\n\n- 阶段一：基础\n- 阶段二：项目")
        );

        String result = MindmapResponseExtractor.extractLatestMindmapData(messages, OBJECT_MAPPER);

        assertNull(result);
    }

    @Test
    @DisplayName("非思维导图工具即使返回同形 JSON 也不会被提取")
    void extractLatestMindmapDataIgnoresOtherToolJson() {
        List<ChatMessage> messages = List.of(
                ToolExecutionResultMessage.from("call-1", "getResumeGuide", MINDMAP_JSON)
        );

        String result = MindmapResponseExtractor.extractLatestMindmapData(messages, OBJECT_MAPPER);

        assertNull(result);
    }
}
