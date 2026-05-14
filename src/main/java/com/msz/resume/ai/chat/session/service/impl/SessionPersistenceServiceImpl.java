package com.msz.resume.ai.chat.session.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.converter.ChatMessageConverter;
import com.msz.resume.ai.chat.session.entity.AiSession;
import com.msz.resume.ai.chat.session.entity.MessageRecord;
import com.msz.resume.ai.chat.session.entity.TimelineActionRecord;
import com.msz.resume.ai.chat.session.mapper.AiSessionMapper;
import com.msz.resume.ai.chat.session.mapper.MessageRecordMapper;
import com.msz.resume.ai.chat.session.mapper.TimelineActionRecordMapper;
import com.msz.resume.ai.chat.session.model.HistoryMessage;
import com.msz.resume.ai.chat.session.model.SessionSnapshot;
import com.msz.resume.ai.chat.session.model.SessionSummary;
import com.msz.resume.ai.chat.session.service.SessionPersistenceService;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 会话持久化服务实现类。
 *
 * 负责会话状态和消息记录的持久化存储与恢复，包括：
 * - 会话完成时保存状态和消息到数据库
 * - 会话恢复时从数据库重建状态
 * - 会话的关闭、重命名、置顶等管理操作
 */
@Slf4j
@Service
public class SessionPersistenceServiceImpl implements SessionPersistenceService {

    private final AiSessionMapper sessionMapper;
    private final MessageRecordMapper messageMapper;
    private final TimelineActionRecordMapper timelineActionMapper;
    private final ChatMessageConverter messageConverter;
    private final ObjectMapper objectMapper;
    private static final int UI_ONLY_ANCHOR_INDEX = -1;
    private static final TypeReference<List<Map<String, Object>>> TASK_PLAN_TYPE = new TypeReference<>() {};
    private static final Set<String> TASK_PLAN_TOOL_NAMES = Set.of(
            "createPlan", "updateStatus", "addTask", "removeTask"
    );

    /** 构造函数：注入会话Mapper、消息Mapper、消息转换器和JSON处理器 */
    public SessionPersistenceServiceImpl(AiSessionMapper sessionMapper,
                                         MessageRecordMapper messageMapper,
                                         TimelineActionRecordMapper timelineActionMapper,
                                         ChatMessageConverter messageConverter,
                                         ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.timelineActionMapper = timelineActionMapper;
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
    }

    /** 完成一轮对话后持久化会话状态和新增消息（upsert会话 + insert增量消息） */
    @Override
    @Transactional
    public void completeRound(String sessionId, String ownerUsername, SessionState state, List<ChatMessage> messages) {
        completeRound(sessionId, ownerUsername, state, messages, List.of());
    }

    /** 完成一轮对话后持久化会话状态、新增消息和本轮用户可见 action timeline */
    @Override
    @Transactional
    public void completeRound(String sessionId,
                              String ownerUsername,
                              SessionState state,
                              List<ChatMessage> messages,
                              List<Map<String, Object>> timelineActions) {
        if (sessionId == null || ownerUsername == null || ownerUsername.isBlank() || state == null) {
            log.warn("[completeRound] invalid arguments: sessionId={}, ownerUsername={}, state={}",
                    sessionId, ownerUsername, state);
            return;
        }

        log.info("[completeRound] save session: sessionId={}, messages={}, inputTokens={}, outputTokens={}",
                sessionId, messages != null ? messages.size() : 0,
                state.getTotalInputTokens(), state.getTotalOutputTokens());

        AiSession session = new AiSession();
        session.setSessionId(sessionId);
        session.setOwnerUsername(ownerUsername);
        session.setTitle(null);
        session.setStatus(state.isActive() ? "active" : "closed");
        session.setPinned(false);
        session.setPinnedAt(null);
        session.setTotalInputTokens(state.getTotalInputTokens());
        session.setTotalOutputTokens(state.getTotalOutputTokens());
        session.setCreatedAt(instantToLocalDateTime(state.getCreatedAt()));
        session.setLastActiveAt(instantToLocalDateTime(state.getLastActiveAt()));

        sessionMapper.upsert(session);

        int existingCount = messageMapper.countBySessionId(sessionId);
        List<MessageRecord> messageRecords = messageConverter.toMessageRecordList(sessionId, messages);
        int anchorMessageIndex = lastAiMessageIndex(messageRecords);

        if (messageRecords.size() > existingCount) {
            List<MessageRecord> newRecords = messageRecords.subList(existingCount, messageRecords.size());
            for (MessageRecord record : newRecords) {
                record.setCreatedAt(LocalDateTime.now());
            }
            messageMapper.insertBatch(newRecords);
            log.info("[completeRound] saved new messages: sessionId={}, added={}, total={}",
                    sessionId, newRecords.size(), messageRecords.size());
        } else {
            log.info("[completeRound] no new messages: sessionId={}, existing={}", sessionId, existingCount);
        }

        timelineActionMapper.deleteUiOnlyPendingBySessionId(sessionId);
        saveTimelineActions(sessionId, anchorMessageIndex, timelineActions);
    }

    @Override
    @Transactional
    public void persistTimelineActions(String sessionId,
                                       String ownerUsername,
                                       SessionState state,
                                       int anchorMessageIndex,
                                       List<Map<String, Object>> timelineActions) {
        if (sessionId == null || ownerUsername == null || ownerUsername.isBlank() || state == null) {
            log.warn("[persistTimelineActions] invalid arguments: sessionId={}, ownerUsername={}, state={}",
                    sessionId, ownerUsername, state);
            return;
        }

        AiSession session = new AiSession();
        session.setSessionId(sessionId);
        session.setOwnerUsername(ownerUsername);
        session.setTitle(null);
        session.setStatus(state.isActive() ? "active" : "closed");
        session.setPinned(false);
        session.setPinnedAt(null);
        session.setTotalInputTokens(state.getTotalInputTokens());
        session.setTotalOutputTokens(state.getTotalOutputTokens());
        session.setCreatedAt(instantToLocalDateTime(state.getCreatedAt()));
        session.setLastActiveAt(instantToLocalDateTime(state.getLastActiveAt()));
        sessionMapper.upsert(session);

        if (anchorMessageIndex == UI_ONLY_ANCHOR_INDEX) {
            timelineActionMapper.deleteUiOnlyPendingBySessionId(sessionId);
        }
        saveTimelineActions(sessionId, anchorMessageIndex, timelineActions);
    }

    /** 恢复会话：从数据库加载会话状态和历史消息，返回SessionSnapshot */
    @Override
    public SessionSnapshot restoreSession(String sessionId, String ownerUsername) {
        if (sessionId == null || sessionId.isBlank() || ownerUsername == null || ownerUsername.isBlank()) {
            return null;
        }

        log.info("[restoreSession] restore session: sessionId={}", sessionId);

        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null
                || "closed".equalsIgnoreCase(session.getStatus())
                || !ownerUsername.equals(session.getOwnerUsername())) {
            log.info("[restoreSession] session unavailable: sessionId={}", sessionId);
            return null;
        }

        List<MessageRecord> messageRecords = messageMapper.selectBySessionId(sessionId);
        List<TimelineActionRecord> timelineActionRecords = timelineActionMapper.selectBySessionId(sessionId);
        List<ChatMessage> messages = messageConverter.toChatMessageList(messageRecords);
        SessionState state = buildSessionState(session, messages);
        List<HistoryMessage> historyMessages = buildHistoryMessages(messageRecords, timelineActionRecords);

        log.info("[restoreSession] restored: sessionId={}, messages={}", sessionId, messages.size());
        return new SessionSnapshot(state, messages, historyMessages);
    }

    /** 关闭会话：将状态设为closed并清除置顶标记 */
    @Override
    @Transactional
    public void closeSession(String sessionId, String ownerUsername) {
        if (sessionId == null || sessionId.isBlank() || ownerUsername == null || ownerUsername.isBlank()) {
            return;
        }

        log.info("[closeSession] close session: sessionId={}", sessionId);

        AiSession session = sessionMapper.selectById(sessionId);
        if (session != null && ownerUsername.equals(session.getOwnerUsername())) {
            session.setStatus("closed");
            session.setPinned(false);
            session.setPinnedAt(null);
            sessionMapper.updateById(session);
        }
    }

    /** 重命名会话：更新会话标题（截断超长标题） */
    @Override
    @Transactional
    public SessionSummary renameSession(String sessionId, String ownerUsername, String title) {
        if (sessionId == null || sessionId.isBlank() || ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }

        int updated = sessionMapper.updateTitle(sessionId, ownerUsername, normalizeTitle(title));
        if (updated == 0) {
            return null;
        }

        return buildSessionSummary(sessionMapper.selectById(sessionId));
    }

    /** 置顶/取消置顶会话 */
    @Override
    @Transactional
    public SessionSummary pinSession(String sessionId, String ownerUsername, boolean pinned) {
        if (sessionId == null || sessionId.isBlank() || ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }

        int updated = sessionMapper.updatePinned(sessionId, ownerUsername, pinned);
        if (updated == 0) {
            return null;
        }

        return buildSessionSummary(sessionMapper.selectById(sessionId));
    }

    /** 查询所有活跃会话列表 */
    @Override
    public List<SessionSummary> listActiveSessions(String ownerUsername) {
        List<SessionSummary> result = sessionMapper.selectActiveSessions(ownerUsername).stream()
                .map(this::buildSessionSummary)
                .toList();

        log.info("[listActiveSessions] ownerUsername={}, active sessions={}", ownerUsername, result.size());
        return result;
    }

    /** 从数据库实体构建SessionState对象 */
    private SessionState buildSessionState(AiSession session, List<ChatMessage> messages) {
        HashMap<String, Object> data = new HashMap<>();
        data.put(SessionState.SESSION_ID, session.getSessionId());
        data.put(SessionState.IS_ACTIVE, "active".equalsIgnoreCase(session.getStatus()));
        data.put(SessionState.TOTAL_INPUT_TOKENS, session.getTotalInputTokens());
        data.put(SessionState.TOTAL_OUTPUT_TOKENS, session.getTotalOutputTokens());
        data.put(SessionState.INNER_STATE, buildRestoredInnerState(messages));

        if (session.getCreatedAt() != null) {
            data.put(SessionState.CREATED_AT, localDateTimeToInstant(session.getCreatedAt()));
        }
        if (session.getLastActiveAt() != null) {
            data.put(SessionState.LAST_ACTIVE_AT, localDateTimeToInstant(session.getLastActiveAt()));
        }

        return new SessionState(data);
    }

    /** 构建恢复后的内层状态QueryLoopState，提取最新任务计划 */
    private QueryLoopState buildRestoredInnerState(List<ChatMessage> messages) {
        HashMap<String, Object> innerData = new HashMap<>();
        innerData.put(QueryLoopState.TASK_PLAN, extractLatestTaskPlan(messages));
        return new QueryLoopState(innerData);
    }

    /** 从消息列表中逆向提取最新的任务计划（从createPlan/updateStatus等工具结果） */
    private List<Map<String, Object>> extractLatestTaskPlan(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (!(message instanceof ToolExecutionResultMessage toolMessage)) {
                continue;
            }

            if (!TASK_PLAN_TOOL_NAMES.contains(toolMessage.toolName())) {
                continue;
            }

            Optional<List<Map<String, Object>>> taskPlan = parseTaskPlanResult(toolMessage.text());
            if (taskPlan.isPresent()) {
                return taskPlan.get();
            }
        }
        return List.of();
    }

    /** 解析工具结果JSON为任务计划列表，校验必需字段 */
    private Optional<List<Map<String, Object>>> parseTaskPlanResult(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return Optional.empty();
        }

        try {
            List<Map<String, Object>> tasks = objectMapper.readValue(toolResult, TASK_PLAN_TYPE);
            boolean validTaskPlan = tasks.stream().allMatch(task ->
                    task.containsKey("taskId")
                            && task.containsKey("description")
                            && task.containsKey("status"));
            return validTaskPlan ? Optional.of(tasks) : Optional.empty();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private int lastAiMessageIndex(List<MessageRecord> messageRecords) {
        if (messageRecords == null || messageRecords.isEmpty()) {
            return UI_ONLY_ANCHOR_INDEX;
        }
        for (int i = messageRecords.size() - 1; i >= 0; i--) {
            MessageRecord record = messageRecords.get(i);
            if ("AI".equals(record.getMessageType())) {
                return i;
            }
        }
        return UI_ONLY_ANCHOR_INDEX;
    }

    private List<HistoryMessage> buildHistoryMessages(List<MessageRecord> messageRecords,
                                                      List<TimelineActionRecord> timelineActionRecords) {
        Map<Integer, List<Map<String, Object>>> timelineActionsByAnchor = timelineActionsByAnchor(timelineActionRecords);

        if ((messageRecords == null || messageRecords.isEmpty()) && timelineActionsByAnchor.isEmpty()) {
            return List.of();
        }

        List<HistoryMessage> historyMessages = new java.util.ArrayList<>();

        if (messageRecords != null) {
            for (int i = 0; i < messageRecords.size(); i++) {
                MessageRecord record = messageRecords.get(i);
                ChatMessage message = messageConverter.toChatMessage(record);
                if (message == null) {
                    continue;
                }
                List<Map<String, Object>> actions = mergeTimelineActions(
                        parseTimelineActions(record.getTimelineActionsJson()),
                        timelineActionsByAnchor.getOrDefault(i, List.of())
                );
                historyMessages.add(new HistoryMessage(record, message, actions, false, null));
            }
        }

        List<Map<String, Object>> uiOnlyActions = timelineActionsByAnchor.getOrDefault(UI_ONLY_ANCHOR_INDEX, List.of());
        if (!uiOnlyActions.isEmpty()) {
            historyMessages.add(HistoryMessage.uiOnly(uiOnlyActions, firstActionCreatedAt(timelineActionRecords, UI_ONLY_ANCHOR_INDEX)));
        }

        return historyMessages;
    }

    private List<Map<String, Object>> parseTimelineActions(String timelineActionsJson) {
        if (timelineActionsJson == null || timelineActionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(timelineActionsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("[restoreSession] timeline actions parse failed: error={}", e.getMessage());
            return List.of();
        }
    }

    private void saveTimelineActions(String sessionId,
                                     int anchorMessageIndex,
                                     List<Map<String, Object>> timelineActions) {
        List<TimelineActionRecord> records = toTimelineActionRecords(sessionId, anchorMessageIndex, timelineActions);
        if (records.isEmpty()) {
            return;
        }
        timelineActionMapper.upsertBatch(records);
        log.info("[completeRound] saved timeline actions: sessionId={}, anchorMessageIndex={}, actions={}",
                sessionId, anchorMessageIndex, records.size());
    }

    private List<TimelineActionRecord> toTimelineActionRecords(String sessionId,
                                                              int anchorMessageIndex,
                                                              List<Map<String, Object>> timelineActions) {
        if (timelineActions == null || timelineActions.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<TimelineActionRecord> records = new java.util.ArrayList<>();
        for (Map<String, Object> action : timelineActions) {
            if (action == null || !booleanValue(action.getOrDefault("persistable", true))) {
                continue;
            }
            String actionId = stringValue(action.get("id"));
            if (actionId.isBlank()) {
                continue;
            }
            try {
                TimelineActionRecord record = new TimelineActionRecord();
                record.setSessionId(sessionId);
                record.setActionId(actionId);
                record.setAnchorMessageIndex(anchorMessageIndex);
                record.setEventType(stringValue(action.get("eventType")));
                record.setKind(stringValue(action.get("kind")));
                record.setFirstSequence(longValue(action.get("firstSequence")));
                record.setSequence(longValue(action.get("sequence")));
                record.setStatus(stringValue(action.get("status")));
                record.setPayloadJson(objectMapper.writeValueAsString(action));
                record.setPromptVisible(booleanValue(action.getOrDefault("promptVisible", false)));
                record.setPersistable(booleanValue(action.getOrDefault("persistable", true)));
                record.setCreatedAt(now);
                record.setUpdatedAt(now);
                records.add(record);
            } catch (JsonProcessingException e) {
                log.warn("[completeRound] timeline action serialize failed: id={}, error={}", actionId, e.getMessage());
            }
        }
        return records;
    }

    private Map<Integer, List<Map<String, Object>>> timelineActionsByAnchor(List<TimelineActionRecord> timelineActionRecords) {
        if (timelineActionRecords == null || timelineActionRecords.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<Map<String, Object>>> actionsByAnchor = new java.util.LinkedHashMap<>();
        for (TimelineActionRecord record : timelineActionRecords) {
            if (Boolean.TRUE.equals(record.getPromptVisible())) {
                continue;
            }
            Map<String, Object> action = parseTimelineAction(record);
            if (action.isEmpty()) {
                continue;
            }
            int anchor = record.getAnchorMessageIndex() != null ? record.getAnchorMessageIndex() : UI_ONLY_ANCHOR_INDEX;
            actionsByAnchor.computeIfAbsent(anchor, ignored -> new java.util.ArrayList<>()).add(action);
        }
        return actionsByAnchor;
    }

    private Map<String, Object> parseTimelineAction(TimelineActionRecord record) {
        if (record == null || record.getPayloadJson() == null || record.getPayloadJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(record.getPayloadJson(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("[restoreSession] timeline action parse failed: id={}, error={}", record.getActionId(), e.getMessage());
            return Map.of();
        }
    }

    private List<Map<String, Object>> mergeTimelineActions(List<Map<String, Object>> legacyActions,
                                                          List<Map<String, Object>> actionStoreActions) {
        if ((legacyActions == null || legacyActions.isEmpty()) && (actionStoreActions == null || actionStoreActions.isEmpty())) {
            return List.of();
        }

        Map<String, Map<String, Object>> mergedById = new java.util.LinkedHashMap<>();
        if (legacyActions != null) {
            for (Map<String, Object> action : legacyActions) {
                putActionById(mergedById, action);
            }
        }
        if (actionStoreActions != null) {
            for (Map<String, Object> action : actionStoreActions) {
                putActionById(mergedById, action);
            }
        }
        return new java.util.ArrayList<>(mergedById.values());
    }

    private void putActionById(Map<String, Map<String, Object>> actionsById, Map<String, Object> action) {
        if (action == null || action.isEmpty()) {
            return;
        }
        String id = stringValue(action.get("id"));
        if (id.isBlank()) {
            id = "timeline_action_" + actionsById.size();
        }
        actionsById.put(id, action);
    }

    private LocalDateTime firstActionCreatedAt(List<TimelineActionRecord> records, int anchorMessageIndex) {
        if (records == null) {
            return null;
        }
        return records.stream()
                .filter(record -> record.getAnchorMessageIndex() != null && record.getAnchorMessageIndex() == anchorMessageIndex)
                .map(TimelineActionRecord::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /** 从数据库实体构建会话摘要对象 */
    private SessionSummary buildSessionSummary(AiSession session) {
        if (session == null) {
            return null;
        }

        return new SessionSummary(
                session.getSessionId(),
                session.getTitle(),
                Boolean.TRUE.equals(session.getPinned()),
                session.getCreatedAt(),
                session.getLastActiveAt()
        );
    }

    /** 标准化标题：去除空白、限制最大长度200字符 */
    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String normalized = title.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > 200) {
            return normalized.substring(0, 200);
        }

        return normalized;
    }

    /** Instant转换为LocalDateTime（系统时区） */
    private LocalDateTime instantToLocalDateTime(Instant instant) {
        if (instant == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /** LocalDateTime转换为Instant（系统时区） */
    private Instant localDateTimeToInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return Instant.now();
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
