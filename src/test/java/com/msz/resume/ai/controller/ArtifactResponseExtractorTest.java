package com.msz.resume.ai.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
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

    @Test
    @DisplayName("支持从 assistant 混杂文本中提取并隐藏 optimize_result JSON")
    void extractLatestArtifactsFromAssistantTextWithEmbeddedJson() {
        String assistantText = """
                我已整理好优化结果：
                {"type":"optimize_result","matchScore":88,"optimizedResume":{"basicInfo":{"name":"莫仕铮"}}}
                可在工作台查看。
                """;

        List<ChatMessage> messages = List.of(
                UserMessage.from("帮我优化简历"),
                AiMessage.from(assistantText)
        );

        List<com.msz.resume.ai.chat.api.dto.ChatArtifact> artifacts =
                ArtifactResponseExtractor.extractLatestArtifacts(messages, OBJECT_MAPPER);
        String stripped = ArtifactResponseExtractor.stripPureArtifactText(assistantText, artifacts, OBJECT_MAPPER);

        assertEquals(1, artifacts.size());
        assertEquals("optimize_result", artifacts.getFirst().getType());
        assertEquals("我已整理好优化结果：\n可在工作台查看。", stripped);
    }

    @Test
    @DisplayName("publishArtifact 成功后不把工具调用前的过渡句当最终回复")
    void visibleAssistantTextIgnoresToolPlanPreambleAfterArtifactReady() {
        String toolResult = """
                {
                  "type": "optimize_result",
                  "payload": {
                    "matchScore": 90,
                    "optimizedResume": {
                      "basicInfo": {
                        "name": "莫仕铮"
                      }
                    }
                  }
                }
                """;
        List<ChatMessage> messages = List.of(
                UserMessage.from("帮我生成优化简历"),
                new AiMessage(
                        "我先把你的简历内容整理成结构化优化结果，并给出更适合投递的版本。",
                        List.of(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("publishArtifact")
                                .arguments("{}")
                                .build())
                ),
                ToolExecutionResultMessage.from("call-1", "publishArtifact", toolResult)
        );
        List<com.msz.resume.ai.chat.api.dto.ChatArtifact> artifacts =
                ArtifactResponseExtractor.extractLatestArtifacts(messages, OBJECT_MAPPER);

        String visibleText = ArtifactResponseExtractor.extractVisibleAssistantText(messages, artifacts, OBJECT_MAPPER);

        assertEquals(1, artifacts.size());
        assertEquals("", visibleText);
    }
}
