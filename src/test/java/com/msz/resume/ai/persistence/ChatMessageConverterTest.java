package com.msz.resume.ai.chat.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.converter.ChatMessageConverter;
import com.msz.resume.ai.chat.session.entity.MessageRecord;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatMessageConverter 单元测试
 * 测试消息的序列化和反序列化逻辑
 */
class ChatMessageConverterTest {

    private ChatMessageConverter messageConverter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        messageConverter = new ChatMessageConverter(objectMapper);
    }

    @Test
    @DisplayName("UserMessage 序列化与反序列化")
    void testUserMessageConversion() {
        String sessionId = UUID.randomUUID().toString();
        String userContent = "你好，这是一条测试消息";

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage(userContent));

        List<MessageRecord> dbMessages = messageConverter.toMessageRecordList(sessionId, messages);

        assertEquals(1, dbMessages.size());
        MessageRecord dbMessage = dbMessages.get(0);
        assertEquals(sessionId, dbMessage.getSessionId());
        assertEquals("USER", dbMessage.getMessageType());
        assertEquals(userContent, dbMessage.getContent());
        assertNull(dbMessage.getToolCallsJson());
        assertNull(dbMessage.getToolResult());

        // 反序列化
        List<ChatMessage> restoredMessages = messageConverter.toChatMessageList(dbMessages);
        assertEquals(1, restoredMessages.size());
        assertTrue(restoredMessages.get(0) instanceof UserMessage);
        assertEquals(userContent, ((UserMessage) restoredMessages.get(0)).singleText());
    }

    @Test
    @DisplayName("AiMessage 含工具调用 - 序列化与反序列化")
    void testAiMessageWithToolCalls() {
        String sessionId = UUID.randomUUID().toString();
        String aiContent = "我需要调用工具";

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call_123")
                .name("getCurrentTime")
                .arguments("{}")
                .build();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(dev.langchain4j.data.message.AiMessage.aiMessage(aiContent, List.of(toolRequest)));

        // 序列化
        List<MessageRecord> dbMessages = messageConverter.toMessageRecordList(sessionId, messages);
        assertEquals(1, dbMessages.size());

        MessageRecord dbMessage = dbMessages.get(0);
        assertEquals("AI", dbMessage.getMessageType());
        assertEquals(aiContent, dbMessage.getContent());

        // 验证工具调用被正确序列化
        String toolCallsJson = dbMessage.getToolCallsJson();
        assertNotNull(toolCallsJson, "ToolCallsJson 不应为 null");
        assertTrue(toolCallsJson.contains("getCurrentTime"), "JSON 应包含工具名称");

        // 反序列化验证
        List<ChatMessage> restoredMessages = messageConverter.toChatMessageList(dbMessages);
        assertEquals(1, restoredMessages.size());

        dev.langchain4j.data.message.AiMessage restoredAiMsg =
            (dev.langchain4j.data.message.AiMessage) restoredMessages.get(0);
        assertEquals(aiContent, restoredAiMsg.text());
        assertNotNull(restoredAiMsg.toolExecutionRequests(), "工具调用列表不应为 null");
        assertEquals(1, restoredAiMsg.toolExecutionRequests().size(), "应有 1 个工具调用");
        assertEquals("getCurrentTime", restoredAiMsg.toolExecutionRequests().get(0).name());
        assertEquals("call_123", restoredAiMsg.toolExecutionRequests().get(0).id());
    }

    @Test
    @DisplayName("ToolExecutionResultMessage 序列化与反序列化")
    void testToolExecutionResultMessageConversion() {
        String sessionId = UUID.randomUUID().toString();
        String toolCallId = "call_456";
        String toolName = "add";
        String toolResult = "40 + 8 = 48";

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ToolExecutionResultMessage(toolCallId, toolName, toolResult));

        List<MessageRecord> dbMessages = messageConverter.toMessageRecordList(sessionId, messages);

        assertEquals(1, dbMessages.size());
        MessageRecord dbMessage = dbMessages.get(0);
        assertEquals(sessionId, dbMessage.getSessionId());
        assertEquals("TOOL_EXECUTION_RESULT", dbMessage.getMessageType());
        assertEquals(toolCallId, dbMessage.getToolCallId());
        assertEquals(toolName, dbMessage.getToolName());
        assertEquals(toolResult, dbMessage.getToolResult());

        // 反序列化
        List<ChatMessage> restoredMessages = messageConverter.toChatMessageList(dbMessages);
        assertEquals(1, restoredMessages.size());
        assertTrue(restoredMessages.get(0) instanceof ToolExecutionResultMessage);

        ToolExecutionResultMessage restoredToolMsg = (ToolExecutionResultMessage) restoredMessages.get(0);
        assertEquals(toolCallId, restoredToolMsg.id());
        assertEquals(toolName, restoredToolMsg.toolName());
        assertEquals(toolResult, restoredToolMsg.text());
    }

    @Test
    @DisplayName("SystemMessage 序列化与反序列化")
    void testSystemMessageConversion() {
        String sessionId = UUID.randomUUID().toString();
        String systemContent = "你是一个AI助手";

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new dev.langchain4j.data.message.SystemMessage(systemContent));

        List<MessageRecord> dbMessages = messageConverter.toMessageRecordList(sessionId, messages);

        assertEquals(1, dbMessages.size());
        assertEquals("SYSTEM", dbMessages.get(0).getMessageType());
        assertEquals(systemContent, dbMessages.get(0).getContent());

        // 反序列化
        List<ChatMessage> restoredMessages = messageConverter.toChatMessageList(dbMessages);
        assertEquals(1, restoredMessages.size());
        assertTrue(restoredMessages.get(0) instanceof dev.langchain4j.data.message.SystemMessage);
        assertEquals(systemContent, ((dev.langchain4j.data.message.SystemMessage) restoredMessages.get(0)).text());
    }

    @Test
    @DisplayName("AiMessage 纯文本序列化与反序列化")
    void testAiMessageTextOnlyConversion() {
        String sessionId = UUID.randomUUID().toString();
        String aiContent = "这是一条AI回复";

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(dev.langchain4j.data.message.AiMessage.aiMessage(aiContent));

        List<MessageRecord> dbMessages = messageConverter.toMessageRecordList(sessionId, messages);

        assertEquals(1, dbMessages.size());
        MessageRecord dbMessage = dbMessages.get(0);
        assertEquals("AI", dbMessage.getMessageType());
        assertEquals(aiContent, dbMessage.getContent());
        assertNull(dbMessage.getToolCallsJson());

        // 反序列化
        List<ChatMessage> restoredMessages = messageConverter.toChatMessageList(dbMessages);
        assertEquals(1, restoredMessages.size());
        assertTrue(restoredMessages.get(0) instanceof dev.langchain4j.data.message.AiMessage);
        assertEquals(aiContent, ((dev.langchain4j.data.message.AiMessage) restoredMessages.get(0)).text());
    }

    @Test
    @DisplayName("空消息列表处理")
    void testEmptyMessages() {
        String sessionId = UUID.randomUUID().toString();

        List<MessageRecord> dbMessages = messageConverter.toMessageRecordList(sessionId, new ArrayList<>());
        assertTrue(dbMessages.isEmpty());

        List<ChatMessage> chatMessages = messageConverter.toChatMessageList(new ArrayList<>());
        assertTrue(chatMessages.isEmpty());
    }

    @Test
    @DisplayName("null 参数处理")
    void testNullHandling() {
        List<MessageRecord> dbMessages = messageConverter.toMessageRecordList("session-id", null);
        assertTrue(dbMessages.isEmpty());

        List<ChatMessage> chatMessages = messageConverter.toChatMessageList(null);
        assertTrue(chatMessages.isEmpty());

        assertNull(messageConverter.toMessageRecord("session-id", null));
        assertNull(messageConverter.toChatMessage(null));
    }
}
