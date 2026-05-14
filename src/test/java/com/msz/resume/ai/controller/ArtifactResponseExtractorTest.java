package com.msz.resume.ai.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArtifactResponseExtractorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("支持从结构化 publishArtifact 工具结果提取 optimize_result")
    void extractLatestArtifactsFromStructuredToolResult() {
        String toolResult = """
                {
                  "type": "optimize_result",
                  "payload": {
                    "matchScore": 72,
                    "optimizedResume": {
                      "basicInfo": {
                        "name": "莫仕铮"
                      }
                    }
                  }
                }
                """;

        List<ChatMessage> messages = List.of(
                ToolExecutionResultMessage.from("call-1", "publishArtifact", toolResult)
        );

        List<com.msz.resume.ai.chat.api.dto.ChatArtifact> artifacts =
                ArtifactResponseExtractor.extractLatestArtifacts(messages, OBJECT_MAPPER);

        assertEquals(1, artifacts.size());
        assertEquals("optimize_result", artifacts.getFirst().getType());
        assertFalse(ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "optimize_result", OBJECT_MAPPER).isBlank());
    }

    @Test
    @DisplayName("支持 assistant 直接输出 optimize_result JSON")
    void extractLatestArtifactsFromAssistantJson() {
        String assistantJson = """
                {
                  "type": "optimize_result",
                  "matchScore": 88,
                  "optimizedResume": {
                    "basicInfo": {
                      "name": "莫仕铮"
                    }
                  }
                }
                """;

        List<ChatMessage> messages = List.of(
                AiMessage.from(assistantJson)
        );

        List<com.msz.resume.ai.chat.api.dto.ChatArtifact> artifacts =
                ArtifactResponseExtractor.extractLatestArtifacts(messages, OBJECT_MAPPER);

        assertEquals(1, artifacts.size());
        assertEquals("optimize_result", artifacts.getFirst().getType());
    }
}
