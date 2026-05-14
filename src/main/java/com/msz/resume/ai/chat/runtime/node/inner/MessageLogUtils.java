package com.msz.resume.ai.chat.runtime.node.inner;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.stream.Collectors;

/**
 * 消息日志工具类
 *
 * 提供消息摘要和截断方法，用于调试日志输出。
 */
final class MessageLogUtils {

    private MessageLogUtils() {}

    /**
     * 把消息内容截断为摘要，用于调试日志
     * 不同消息类型的摘要格式不同，避免日志过长
     */
    static String summarizeMessage(ChatMessage msg) {
        if (msg instanceof AiMessage aiMsg) {
            String text = aiMsg.text();
            String tools = aiMsg.hasToolExecutionRequests()
                    ? aiMsg.toolExecutionRequests().stream()
                    .map(r -> r.name())
                    .collect(Collectors.joining(","))
                    : "none";
            return String.format("text=%s, tools=[%s]",
                    text != null ? summarizeContent(text) : "null",
                    tools);
        } else if (msg instanceof UserMessage userMsg) {
            return summarizeContent(userMsg.singleText());
        } else if (msg instanceof SystemMessage sysMsg) {
            return "(系统提示词) " + summarizeContent(sysMsg.text());
        } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
            return "(工具结果) " + summarizeContent(toolMsg.text());
        }
        return "unknown";
    }

    /**
     * 内容摘要：长度 + 前200字符预览
     */
    static String summarizeContent(String content) {
        if (content == null) return "null";
        int len = content.length();
        if (len <= 200) {
            return content;
        }
        return String.format("(%d字符) %s...", len, content.substring(0, 200));
    }

    /**
     * 截断字符串到指定长度，超出部分用"..."表示
     */
    static String truncate(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
