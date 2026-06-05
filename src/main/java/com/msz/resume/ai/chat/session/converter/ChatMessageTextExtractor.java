package com.msz.resume.ai.chat.session.converter;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts prompt-visible text from LangChain4j messages without assuming user messages are text-only.
 */
public final class ChatMessageTextExtractor {

    private ChatMessageTextExtractor() {
    }

    public static String extract(ChatMessage message) {
        if (message == null) {
            return "";
        }
        if (message instanceof UserMessage userMessage) {
            return userText(userMessage);
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text() != null ? systemMessage.text() : "";
        }
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text() != null ? aiMessage.text() : "";
        }
        if (message instanceof ToolExecutionResultMessage toolMessage) {
            return toolMessage.text() != null ? toolMessage.text() : "";
        }
        return "";
    }

    public static String userText(UserMessage userMessage) {
        if (userMessage == null) {
            return "";
        }
        if (userMessage.hasSingleText()) {
            return userMessage.singleText();
        }

        List<String> parts = new ArrayList<>();
        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent textContent) {
                if (textContent.text() != null && !textContent.text().isBlank()) {
                    parts.add(textContent.text());
                }
            } else if (content instanceof ImageContent imageContent) {
                String mimeType = imageContent.image() != null ? imageContent.image().mimeType() : null;
                parts.add("[图片" + (mimeType != null && !mimeType.isBlank() ? ": " + mimeType : "") + "]");
            }
        }
        return String.join("\n", parts);
    }

    public static int textLength(ChatMessage message) {
        return extract(message).length();
    }
}
