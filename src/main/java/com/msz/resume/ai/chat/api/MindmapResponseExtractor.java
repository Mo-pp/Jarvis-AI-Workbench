package com.msz.resume.ai.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.List;

/**
 * Mindmap 工具结果提取器。
 *
 * 从消息历史中逆向查找最近的 Mindmap 工具结果，用于响应体中单独返回。
 */
final class MindmapResponseExtractor {

    private static final String MINDMAP_TYPE = "mindmap";
    private static final String MINDMAP_TOOL_NAME = "generateMindmap";

    private MindmapResponseExtractor() {
    }

    /** 从消息列表中提取最新的 Mindmap 工具结果 JSON */
    static String extractLatestMindmapData(List<ChatMessage> messages, ObjectMapper objectMapper) {
        if (messages == null || messages.isEmpty() || objectMapper == null) {
            return null;
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage toolMessage) {
                if (!MINDMAP_TOOL_NAME.equals(toolMessage.toolName())) {
                    continue;
                }
                String text = toolMessage.text();
                if (isMindmapJson(text, objectMapper)) {
                    return text;
                }
            }
        }

        return null;
    }

    /** 判断字符串是否为有效的 Mindmap JSON 结果 */
    private static boolean isMindmapJson(String text, ObjectMapper objectMapper) {
        if (text == null || text.isBlank()) {
            return false;
        }

        try {
            JsonNode node = objectMapper.readTree(text);
            JsonNode markdown = node.get("markdown");
            return MINDMAP_TYPE.equals(node.path("type").asText())
                    && markdown != null
                    && markdown.isTextual()
                    && !markdown.asText().isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }
}
