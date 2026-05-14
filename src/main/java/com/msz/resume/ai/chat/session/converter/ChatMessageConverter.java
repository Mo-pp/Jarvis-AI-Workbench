/**
 * ChatMessage 转换器：实现数据库实体与 LangChain4j ChatMessage 之间的双向转换
 * 用于会话恢复时将数据库记录转换为内存对象，或将内存消息保存到数据库
 */
package com.msz.resume.ai.chat.session.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.entity.MessageRecord;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChatMessageConverter {

    private final ObjectMapper objectMapper;

    public ChatMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 将 MessageRecord 列表转换为 LangChain4j ChatMessage 列表 */
    public List<ChatMessage> toChatMessageList(List<MessageRecord> messageRecords) {
        if (messageRecords == null || messageRecords.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChatMessage> messages = new ArrayList<>();
        for (MessageRecord record : messageRecords) {
            ChatMessage chatMessage = toChatMessage(record);
            if (chatMessage != null) {
                messages.add(chatMessage);
            }
        }
        return messages;
    }

    /** 将单个数据库 MessageRecord 转换为 LangChain4j ChatMessage */
    public ChatMessage toChatMessage(MessageRecord record) {
        if (record == null) {
            return null;
        }

        String messageType = record.getMessageType();
        if (messageType == null) {
            return null;
        }

        return switch (messageType) {
            case "USER" -> new UserMessage(record.getContent());
            case "AI" -> {
                // Dashscope API 要求 AiMessage content 不能为 null
                // 兼容旧数据：数据库中可能存在 content=null 的记录
                String content = record.getContent() != null ? record.getContent() : "";
                String toolCallsJson = record.getToolCallsJson();
                if (toolCallsJson != null && !toolCallsJson.isEmpty()) {
                    try {
                        // 反序列化为 ToolCallDto 列表，再转换为 ToolExecutionRequest
                        List<ToolCallDto> toolCallDtos = objectMapper.readValue(
                                toolCallsJson,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, ToolCallDto.class)
                        );
                        List<ToolExecutionRequest> toolRequests = toolCallDtos.stream()
                                .map(ToolCallDto::toToolExecutionRequest)
                                .toList();
                        yield dev.langchain4j.data.message.AiMessage.aiMessage(content, toolRequests);
                    } catch (JsonProcessingException e) {
                        yield dev.langchain4j.data.message.AiMessage.aiMessage(content);
                    }
                }
                yield dev.langchain4j.data.message.AiMessage.aiMessage(content);
            }
            case "SYSTEM" -> new SystemMessage(record.getContent());
            case "TOOL_EXECUTION_RESULT" -> new ToolExecutionResultMessage(
                    record.getToolCallId(),
                    record.getToolName(),
                    record.getToolResult()
            );
            default -> null;
        };
    }

    /** 将 LangChain4j ChatMessage 列表转换为数据库 MessageRecord 列表 */
    public List<MessageRecord> toMessageRecordList(String sessionId, List<ChatMessage> chatMessages) {
        if (chatMessages == null || chatMessages.isEmpty()) {
            return new ArrayList<>();
        }

        List<MessageRecord> records = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessages) {
            MessageRecord record = toMessageRecord(sessionId, chatMessage);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    /** 将单个 LangChain4j ChatMessage 转换为数据库 MessageRecord */
    public MessageRecord toMessageRecord(String sessionId, ChatMessage chatMessage) {
        if (chatMessage == null) {
            return null;
        }

        MessageRecord record = new MessageRecord();
        record.setSessionId(sessionId);
        record.setIsCompressed(false);  // 默认未压缩

        if (chatMessage instanceof UserMessage userMessage) {
            record.setMessageType("USER");
            record.setContent(userMessage.singleText());
        } else if (chatMessage instanceof dev.langchain4j.data.message.AiMessage lcAiMsg) {
            record.setMessageType("AI");
            // Dashscope API 要求 AiMessage content 不能为 null
            // 当 LLM 只返回工具调用时 text() 为 null，必须存储为空字符串
            record.setContent(lcAiMsg.text() != null ? lcAiMsg.text() : "");
            List<ToolExecutionRequest> toolRequests = lcAiMsg.toolExecutionRequests();
            if (toolRequests != null && !toolRequests.isEmpty()) {
                try {
                    // 转换为 ToolCallDto 列表后序列化
                    List<ToolCallDto> toolCallDtos = toolRequests.stream()
                            .map(ToolCallDto::from)
                            .toList();
                    record.setToolCallsJson(objectMapper.writeValueAsString(toolCallDtos));
                } catch (JsonProcessingException e) {
                    // 忽略序列化错误
                }
            }
        } else if (chatMessage instanceof SystemMessage systemMessage) {
            record.setMessageType("SYSTEM");
            record.setContent(systemMessage.text());
        } else if (chatMessage instanceof ToolExecutionResultMessage toolResultMsg) {
            record.setMessageType("TOOL_EXECUTION_RESULT");
            record.setToolResult(toolResultMsg.text());
            record.setToolCallId(toolResultMsg.id());
            record.setToolName(toolResultMsg.toolName());
        } else {
            return null;
        }

        return record;
    }
}
