/**
 * ChatMessage 转换器：实现数据库实体与 LangChain4j ChatMessage 之间的双向转换
 * 用于会话恢复时将数据库记录转换为内存对象，或将内存消息保存到数据库
 */
package com.msz.resume.ai.chat.session.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.entity.MessageRecord;
import com.msz.resume.ai.file.dto.ParsedFile;
import com.msz.resume.ai.file.service.FileStorageService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatMessageConverter {

    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private static final TypeReference<List<AttachmentMetadata>> ATTACHMENT_METADATA_TYPE = new TypeReference<>() {};

    public ChatMessageConverter(ObjectMapper objectMapper, FileStorageService fileStorageService) {
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
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
            case "USER" -> restoreUserMessage(record);
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
            record.setContent(persistentUserText(userMessage));
            String attachmentsJson = attachmentsJson(userMessage);
            if (attachmentsJson != null) {
                record.setAttachmentsJson(attachmentsJson);
            }
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

    private UserMessage restoreUserMessage(MessageRecord record) {
        List<AttachmentMetadata> attachments = parseAttachments(record.getAttachmentsJson());
        String text = record.getContent() != null ? record.getContent() : "";
        if (attachments.isEmpty()) {
            return new UserMessage(text);
        }

        StringBuilder textBuilder = new StringBuilder(text);
        List<Content> imageContents = new ArrayList<>();
        List<AttachmentMetadata> restoredAttachments = new ArrayList<>();
        for (AttachmentMetadata attachment : attachments) {
            if (!"image".equals(attachment.getFileKind())) {
                restoredAttachments.add(attachment);
                continue;
            }
            ParsedFile image = restoreImageAttachment(attachment);
            if (image != null) {
                imageContents.add(ImageContent.from(image.getBase64Data(), image.getMimeType()));
                restoredAttachments.add(toAttachmentMetadata(image, true));
            } else {
                appendSummary(textBuilder, imageSummary(attachment));
                attachment.setAvailable(false);
                restoredAttachments.add(attachment);
            }
        }
        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from(textBuilder.toString()));
        contents.addAll(imageContents);
        return UserMessage.builder()
                .contents(contents)
                .attributes(Map.of("attachments", restoredAttachments))
                .build();
    }

    private void appendSummary(StringBuilder textBuilder, String summary) {
        if (!textBuilder.isEmpty()) {
            textBuilder.append("\n");
        }
        textBuilder.append(summary);
    }

    private ParsedFile restoreImageAttachment(AttachmentMetadata attachment) {
        if (fileStorageService == null
                || attachment == null
                || !attachment.isAvailable()
                || attachment.getFileId() == null
                || attachment.getFileId().isBlank()) {
            return null;
        }
        return fileStorageService.get(attachment.getFileId())
                .filter(parsedFile -> parsedFile.isSuccess()
                        && "image".equals(parsedFile.getFileKind())
                        && parsedFile.getBase64Data() != null
                        && !parsedFile.getBase64Data().isBlank()
                        && parsedFile.getMimeType() != null
                        && !parsedFile.getMimeType().isBlank())
                .orElse(null);
    }

    private AttachmentMetadata toAttachmentMetadata(ParsedFile parsedFile, boolean available) {
        return AttachmentMetadata.builder()
                .fileId(parsedFile.getFileId())
                .fileName(parsedFile.getFileName())
                .fileType(parsedFile.getFileType())
                .fileKind(parsedFile.getFileKind())
                .mimeType(parsedFile.getMimeType())
                .fileSize(parsedFile.getFileSize())
                .available(available)
                .build();
    }

    private List<AttachmentMetadata> parseAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(attachmentsJson, ATTACHMENT_METADATA_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String attachmentsJson(UserMessage userMessage) {
        if (userMessage == null) {
            return null;
        }
        List<AttachmentMetadata> attributeAttachments = attachmentsFromAttributes(userMessage);
        if (!attributeAttachments.isEmpty()) {
            try {
                return objectMapper.writeValueAsString(attributeAttachments);
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        if (userMessage.hasSingleText()) {
            return null;
        }
        List<AttachmentMetadata> attachments = new ArrayList<>();
        for (Content content : userMessage.contents()) {
            if (content instanceof ImageContent imageContent) {
                Image image = imageContent.image();
                attachments.add(AttachmentMetadata.builder()
                        .fileKind("image")
                        .mimeType(image != null ? image.mimeType() : null)
                        .available(false)
                        .build());
            }
        }
        if (attachments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String persistentUserText(UserMessage userMessage) {
        if (userMessage == null) {
            return "";
        }
        if (userMessage.hasSingleText()) {
            return userMessage.singleText();
        }
        List<String> parts = new ArrayList<>();
        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent textContent
                    && textContent.text() != null
                    && !textContent.text().isBlank()) {
                parts.add(textContent.text());
            }
        }
        return String.join("\n", parts);
    }

    private List<AttachmentMetadata> attachmentsFromAttributes(UserMessage userMessage) {
        Map<String, Object> attributes = userMessage.attributes();
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }
        Object rawAttachments = attributes.get("attachments");
        if (!(rawAttachments instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<AttachmentMetadata> attachments = new ArrayList<>();
        for (Object raw : rawList) {
            if (raw instanceof AttachmentMetadata metadata) {
                attachments.add(metadata);
            } else if (raw instanceof Map<?, ?> map) {
                attachments.add(fromMap(map));
            }
        }
        return attachments;
    }

    private AttachmentMetadata fromMap(Map<?, ?> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return objectMapper.convertValue(normalized, AttachmentMetadata.class);
    }

    private String imageSummary(AttachmentMetadata attachment) {
        String name = attachment.getFileName();
        if (name == null || name.isBlank()) {
            name = attachment.getMimeType();
        }
        if (name == null || name.isBlank()) {
            name = "image";
        }
        return "[图片: " + name + "]";
    }
}
