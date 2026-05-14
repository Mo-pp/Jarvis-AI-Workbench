package com.msz.resume.ai.chat.session.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.converter.ChatMessageConverter;
import com.msz.resume.ai.chat.session.entity.AiSession;
import com.msz.resume.ai.chat.session.entity.MessageRecord;
import com.msz.resume.ai.chat.session.entity.TimelineActionRecord;
import com.msz.resume.ai.chat.session.mapper.AiSessionMapper;
import com.msz.resume.ai.chat.session.mapper.MessageRecordMapper;
import com.msz.resume.ai.chat.session.mapper.TimelineActionRecordMapper;
import com.msz.resume.ai.chat.session.model.SessionSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPersistenceServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AiSession> sessions = new HashMap<>();
    private final Map<String, List<MessageRecord>> messagesBySession = new HashMap<>();
    private final Map<String, List<TimelineActionRecord>> actionsBySession = new HashMap<>();
    private int deletedUiOnlyPendingCount = 0;
    private final AiSessionMapper sessionMapper = mapperProxy(AiSessionMapper.class, this::handleSessionMapper);
    private final MessageRecordMapper messageMapper = mapperProxy(MessageRecordMapper.class, this::handleMessageMapper);
    private final TimelineActionRecordMapper timelineActionMapper = mapperProxy(TimelineActionRecordMapper.class, this::handleTimelineActionMapper);
    private final SessionPersistenceServiceImpl service = new SessionPersistenceServiceImpl(
            sessionMapper,
            messageMapper,
            timelineActionMapper,
            new ChatMessageConverter(objectMapper),
            objectMapper
    );

    @Test
    @DisplayName("恢复会话时从最新任务工具结果恢复任务计划")
    void restoreSessionRestoresLatestTaskPlanFromTaskToolResult() throws JsonProcessingException {
        String sessionId = "session-task-plan";
        sessions.put(sessionId, activeSession(sessionId));
        actionsBySession.put(sessionId, List.of());
        messagesBySession.put(sessionId, List.of(
                taskToolResult(sessionId, "createPlan", List.of(
                        task("task-1", "收集需求", "pending")
                )),
                taskToolResult(sessionId, "updateStatus", List.of(
                        task("task-1", "收集需求", "completed")
                ))
        ));

        SessionSnapshot snapshot = service.restoreSession(sessionId, "test-user");

        assertNotNull(snapshot);
        List<Map<String, Object>> taskPlan = snapshot.state().getInnerState().getTaskPlan();
        assertEquals(1, taskPlan.size());
        assertEquals("task-1", taskPlan.get(0).get("taskId"));
        assertEquals("completed", taskPlan.get(0).get("status"));
    }

    @Test
    @DisplayName("恢复会话时保留最后一次合法空任务计划")
    void restoreSessionKeepsLatestEmptyTaskPlan() throws JsonProcessingException {
        String sessionId = "session-empty-task-plan";
        sessions.put(sessionId, activeSession(sessionId));
        actionsBySession.put(sessionId, List.of());
        messagesBySession.put(sessionId, List.of(
                taskToolResult(sessionId, "createPlan", List.of(
                        task("task-1", "收集需求", "pending")
                )),
                taskToolResult(sessionId, "removeTask", List.of())
        ));

        SessionSnapshot snapshot = service.restoreSession(sessionId, "test-user");

        assertNotNull(snapshot);
        assertEquals(0, snapshot.state().getInnerState().getTaskPlan().size());
    }

    @Test
    @DisplayName("恢复会话时 UI timeline action 从独立表合并但不进入 LLM 消息历史")
    void restoreSessionMergesTimelineActionsWithoutPromptHistoryPollution() throws JsonProcessingException {
        String sessionId = "session-actions";
        MessageRecord aiMessage = aiMessage(sessionId, 10L, "已完成");

        sessions.put(sessionId, activeSession(sessionId));
        messagesBySession.put(sessionId, List.of(aiMessage));
        actionsBySession.put(sessionId, List.of(
                timelineAction(sessionId, 0, Map.of(
                        "id", "tool_action_call_1",
                        "kind", "tool_use",
                        "eventType", "tool_use_result",
                        "toolName", "openviking_read",
                        "title", "读取资源",
                        "status", "success",
                        "summary", "读取完成",
                        "promptVisible", false
                ))
        ));

        SessionSnapshot snapshot = service.restoreSession(sessionId, "test-user");

        assertNotNull(snapshot);
        assertEquals(1, snapshot.messages().size());
        assertEquals(1, snapshot.historyMessages().size());
        assertFalse(snapshot.historyMessages().getFirst().uiOnly());
        assertEquals(1, snapshot.historyMessages().getFirst().timelineActions().size());
        assertEquals("tool_action_call_1", snapshot.historyMessages().getFirst().timelineActions().getFirst().get("id"));
    }

    @Test
    @DisplayName("恢复会话时 pending UI-only timeline action 不需要对应 LLM 消息")
    void restoreSessionReturnsUiOnlyPendingTimelineAction() throws JsonProcessingException {
        String sessionId = "session-pending-action";
        sessions.put(sessionId, activeSession(sessionId));
        messagesBySession.put(sessionId, List.of());
        actionsBySession.put(sessionId, List.of(
                timelineAction(sessionId, -1, Map.of(
                        "id", "user_question_pending-1",
                        "kind", "user_question",
                        "eventType", "pending",
                        "title", "需要你补充信息",
                        "status", "pending",
                        "summary", "等待你的回答",
                        "questionCount", 1,
                        "promptVisible", false
                ))
        ));

        SessionSnapshot snapshot = service.restoreSession(sessionId, "test-user");

        assertNotNull(snapshot);
        assertEquals(0, snapshot.messages().size());
        assertEquals(1, snapshot.historyMessages().size());
        assertEquals(true, snapshot.historyMessages().getFirst().uiOnly());
        assertEquals("user_question_pending-1", snapshot.historyMessages().getFirst().timelineActions().getFirst().get("id"));
    }

    @Test
    @DisplayName("恢复会话时 pending UI-only timeline action 排在已有消息之后")
    void restoreSessionAppendsUiOnlyPendingTimelineActionAfterMessages() throws JsonProcessingException {
        String sessionId = "session-pending-after-messages";
        sessions.put(sessionId, activeSession(sessionId));
        messagesBySession.put(sessionId, List.of(aiMessage(sessionId, 10L, "先前回复")));
        actionsBySession.put(sessionId, List.of(
                timelineAction(sessionId, -1, Map.of(
                        "id", "user_question_pending-2",
                        "kind", "user_question",
                        "eventType", "pending",
                        "title", "需要你补充信息",
                        "status", "pending",
                        "summary", "等待你的回答",
                        "questionCount", 1,
                        "promptVisible", false
                ))
        ));

        SessionSnapshot snapshot = service.restoreSession(sessionId, "test-user");

        assertNotNull(snapshot);
        assertEquals(2, snapshot.historyMessages().size());
        assertFalse(snapshot.historyMessages().get(0).uiOnly());
        assertTrue(snapshot.historyMessages().get(1).uiOnly());
    }

    @Test
    @DisplayName("完整持久化会清理旧的 UI-only pending action")
    void completeRoundDeletesResolvedUiOnlyPendingTimelineAction() {
        String sessionId = "session-complete-clears-pending";
        sessions.put(sessionId, activeSession(sessionId));
        messagesBySession.put(sessionId, List.of());

        service.completeRound(
                sessionId,
                "test-user",
                activeState(sessionId),
                List.of(dev.langchain4j.data.message.AiMessage.aiMessage("完成")),
                List.of()
        );

        assertEquals(1, deletedUiOnlyPendingCount);
    }

    @Test
    @DisplayName("只持久化 pending timeline action 会先清理旧的 UI-only pending")
    void persistTimelineActionsDeletesPreviousUiOnlyPendingTimelineAction() {
        String sessionId = "session-replaces-pending-action";
        sessions.put(sessionId, activeSession(sessionId));

        service.persistTimelineActions(
                sessionId,
                "test-user",
                activeState(sessionId),
                -1,
                List.of(Map.of(
                        "id", "user_question_pending-new",
                        "kind", "user_question",
                        "eventType", "pending",
                        "title", "需要你补充信息",
                        "status", "pending",
                        "summary", "等待你的回答",
                        "questionCount", 1,
                        "promptVisible", false,
                        "persistable", true
                ))
        );

        assertEquals(1, deletedUiOnlyPendingCount);
        assertEquals(1, actionsBySession.get(sessionId).size());
        assertEquals("user_question_pending-new", actionsBySession.get(sessionId).getFirst().getActionId());
    }

    private Object handleSessionMapper(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "selectById" -> sessions.get(String.valueOf(args[0]));
            case "upsert" -> {
                AiSession session = (AiSession) args[0];
                sessions.put(session.getSessionId(), session);
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        };
    }

    private Object handleMessageMapper(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "selectBySessionId" -> messagesBySession.getOrDefault(String.valueOf(args[0]), List.of());
            case "countBySessionId" -> messagesBySession.getOrDefault(String.valueOf(args[0]), List.of()).size();
            default -> defaultValue(method.getReturnType());
        };
    }

    @SuppressWarnings("unchecked")
    private Object handleTimelineActionMapper(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "selectBySessionId" -> actionsBySession.getOrDefault(String.valueOf(args[0]), List.of());
            case "deleteUiOnlyPendingBySessionId" -> {
                deletedUiOnlyPendingCount++;
                yield 1;
            }
            case "upsertBatch" -> {
                List<TimelineActionRecord> records = (List<TimelineActionRecord>) args[0];
                records.forEach(record -> {
                    List<TimelineActionRecord> existing = actionsBySession.getOrDefault(record.getSessionId(), List.of());
                    Map<String, TimelineActionRecord> byActionId = new java.util.LinkedHashMap<>();
                    existing.forEach(item -> byActionId.put(item.getActionId(), item));
                    byActionId.put(record.getActionId(), record);
                    actionsBySession.put(record.getSessionId(), new ArrayList<>(byActionId.values()));
                });
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapperProxy(Class<T> mapperType, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> mapperType.getSimpleName() + "TestProxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return handler.invoke(proxy, method, args);
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE || returnType == Short.TYPE || returnType == Byte.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        return null;
    }

    private AiSession activeSession(String sessionId) {
        AiSession session = new AiSession();
        session.setSessionId(sessionId);
        session.setOwnerUsername("test-user");
        session.setStatus("active");
        session.setPinned(false);
        session.setTotalInputTokens(0);
        session.setTotalOutputTokens(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActiveAt(LocalDateTime.now());
        return session;
    }

    private com.msz.resume.ai.chat.runtime.state.SessionState activeState(String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put(com.msz.resume.ai.chat.runtime.state.SessionState.SESSION_ID, sessionId);
        data.put(com.msz.resume.ai.chat.runtime.state.SessionState.IS_ACTIVE, true);
        data.put(com.msz.resume.ai.chat.runtime.state.SessionState.TOTAL_INPUT_TOKENS, 1);
        data.put(com.msz.resume.ai.chat.runtime.state.SessionState.TOTAL_OUTPUT_TOKENS, 1);
        data.put(com.msz.resume.ai.chat.runtime.state.SessionState.CREATED_AT, Instant.now());
        data.put(com.msz.resume.ai.chat.runtime.state.SessionState.LAST_ACTIVE_AT, Instant.now());
        return new com.msz.resume.ai.chat.runtime.state.SessionState(data);
    }

    private MessageRecord taskToolResult(
            String sessionId,
            String toolName,
            List<Map<String, Object>> taskPlan) throws JsonProcessingException {
        MessageRecord record = new MessageRecord();
        record.setSessionId(sessionId);
        record.setMessageType("TOOL_EXECUTION_RESULT");
        record.setToolCallId("call-" + toolName);
        record.setToolName(toolName);
        record.setToolResult(objectMapper.writeValueAsString(taskPlan));
        record.setIsCompressed(false);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private MessageRecord aiMessage(String sessionId, long id, String content) {
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setSessionId(sessionId);
        record.setMessageType("AI");
        record.setContent(content);
        record.setIsCompressed(false);
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private TimelineActionRecord timelineAction(
            String sessionId,
            int anchorMessageIndex,
            Map<String, Object> payload) throws JsonProcessingException {
        TimelineActionRecord record = new TimelineActionRecord();
        record.setSessionId(sessionId);
        record.setActionId(String.valueOf(payload.get("id")));
        record.setAnchorMessageIndex(anchorMessageIndex);
        record.setEventType(String.valueOf(payload.get("eventType")));
        record.setKind(String.valueOf(payload.get("kind")));
        record.setFirstSequence(1L);
        record.setSequence(1L);
        record.setStatus(String.valueOf(payload.get("status")));
        record.setPayloadJson(objectMapper.writeValueAsString(payload));
        record.setPromptVisible(false);
        record.setPersistable(true);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    private Map<String, Object> task(String taskId, String description, String status) {
        return Map.of(
                "taskId", taskId,
                "description", description,
                "detail", "",
                "status", status,
                "createdAt", 1L,
                "updatedAt", 1L
        );
    }
}
