package com.msz.resume.ai.chat.application.pending.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.chat.application.pending.PendingSessionService;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.tooling.dto.PendingSession;
import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import com.msz.resume.ai.chat.tooling.dto.UserAnswerDto;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PendingSessionService 实现：基于 Redis 的挂起会话存储
 *
 * <p>用于 AskUserQuestion 工具的阻塞式实现，存储挂起的会话状态，
 * 等待用户回答后恢复执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingSessionServiceImpl implements PendingSessionService {

    private static final String KEY_PREFIX = "pending:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String pendingId, String sessionId, QueryLoopState state,
                     String toolCallId, List<QuestionDto> questions) {
        saveWithReplacementRequest(pendingId, sessionId, state, toolCallId, questions, null, null);
    }

    @Override
    public void saveWithReplacementRequest(String pendingId, String sessionId, QueryLoopState state,
                                            String toolCallId, List<QuestionDto> questions,
                                            ToolExecutionRequest replacementRequest,
                                            String confirmationToken) {
        try {
            // 序列化状态数据
            Map<String, Object> stateMap = serializeStateToMap(state);
            String stateJson = objectMapper.writeValueAsString(stateMap);
            String userContextJson = state.getUserContext() != null
                    ? objectMapper.writeValueAsString(state.getUserContext())
                    : null;
            String replacementJson = replacementRequest != null
                    ? objectMapper.writeValueAsString(replacementRequest)
                    : null;

            PendingSession session = PendingSession.builder()
                    .pendingId(pendingId)
                    .sessionId(sessionId)
                    .queryLoopStateJson(stateJson)
                    .toolCallId(toolCallId)
                    .toolName(resolveToolName(state, toolCallId))
                    .questions(questions)
                    .createdAt(LocalDateTime.now())
                    .userContextJson(userContextJson)
                    .openVikingIdentity(state.getOpenVikingIdentity())
                    .replacementToolRequestJson(replacementJson)
                    .confirmationToken(confirmationToken)
                    .build();

            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(KEY_PREFIX + pendingId, json, TTL);

            log.info("[PendingSessionService] 保存挂起会话: pendingId={}, sessionId={}, questionsCount={}",
                    pendingId, sessionId, questions.size());
        } catch (JsonProcessingException e) {
            log.error("[PendingSessionService] 序列化失败", e);
            throw new RuntimeException("保存挂起会话失败", e);
        }
    }

    /**
     * 将 QueryLoopState 序列化为可 JSON 化的 Map
     */
    private Map<String, Object> serializeStateToMap(QueryLoopState state) {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", state.getSessionId());
        map.put("turnCount", state.getTurnCount());
        map.put("nudgeCount", state.getNudgeCount());
        map.put("lowYieldCount", state.getLowYieldCount());
        map.put("lastOutputTokenCount", state.getLastOutputTokenCount());
        map.put("discoveredTools", new ArrayList<>(state.getDiscoveredTools()));
        map.put("taskPlan", state.getTaskPlan());
        map.put("surfacedOpenVikingUris", state.getSurfacedOpenVikingUris());
        map.put("isSubAgent", state.isSubAgent());
        map.put("availableTools", new ArrayList<>(state.getAvailableTools()));
        map.put("maxTurns", state.getMaxTurns());
        map.put("subAgentTask", state.getSubAgentTask());
        map.put("subAgentInputTokens", state.getSubAgentInputTokens());
        map.put("subAgentOutputTokens", state.getSubAgentOutputTokens());
        map.put("subAgentTokenAccumulator", state.getSubAgentTokenAccumulator());
        map.put("openVikingIdentity", state.getOpenVikingIdentity());

        // 序列化消息列表
        List<Map<String, Object>> messagesList = new ArrayList<>();
        for (ChatMessage msg : state.getMessages()) {
            Map<String, Object> msgMap = serializeMessage(msg);
            if (msgMap != null) {
                messagesList.add(msgMap);
            }
        }
        map.put("messages", messagesList);

        return map;
    }

    /**
     * 序列化单条消息
     */
    private Map<String, Object> serializeMessage(ChatMessage message) {
        Map<String, Object> map = new HashMap<>();
        if (message instanceof dev.langchain4j.data.message.UserMessage userMsg) {
            map.put("type", "USER");
            map.put("content", userMsg.singleText());
        } else if (message instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
            map.put("type", "AI");
            map.put("content", aiMsg.text() != null ? aiMsg.text() : "");
            if (aiMsg.toolExecutionRequests() != null && !aiMsg.toolExecutionRequests().isEmpty()) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (dev.langchain4j.agent.tool.ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    Map<String, Object> tc = new HashMap<>();
                    tc.put("id", req.id());
                    tc.put("name", req.name());
                    tc.put("arguments", req.arguments());
                    toolCalls.add(tc);
                }
                map.put("toolCalls", toolCalls);
            }
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
            map.put("type", "SYSTEM");
            map.put("content", sysMsg.text());
        } else if (message instanceof dev.langchain4j.data.message.ToolExecutionResultMessage toolResult) {
            map.put("type", "TOOL_EXECUTION_RESULT");
            map.put("id", toolResult.id());
            map.put("toolName", toolResult.toolName());
            map.put("content", toolResult.text());
        }
        return map.isEmpty() ? null : map;
    }

    @Override
    public PendingSession get(String pendingId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + pendingId);
        if (json == null) {
            log.debug("[PendingSessionService] 挂起会话不存在或已过期: pendingId={}", pendingId);
            return null;
        }
        try {
            return objectMapper.readValue(json, PendingSession.class);
        } catch (JsonProcessingException e) {
            log.error("[PendingSessionService] 反序列化失败: pendingId={}", pendingId, e);
            return null;
        }
    }

    @Override
    public void delete(String pendingId) {
        redisTemplate.delete(KEY_PREFIX + pendingId);
        log.info("[PendingSessionService] 删除挂起会话: pendingId={}", pendingId);
    }

    @Override
    public boolean exists(String pendingId) {
        Boolean exists = redisTemplate.hasKey(KEY_PREFIX + pendingId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<ChatMessage> deserializeMessages(PendingSession pendingSession) {
        if (pendingSession == null || pendingSession.getQueryLoopStateJson() == null) {
            return Collections.emptyList();
        }
        return deserializeMessages(pendingSession.getQueryLoopStateJson());
    }

    @Override
    public String resolveToolResultName(PendingSession pendingSession) {
        if (pendingSession != null && pendingSession.getToolName() != null && !pendingSession.getToolName().isBlank()) {
            return pendingSession.getToolName();
        }

        // 兼容旧 Redis 挂起数据。
        return "askUserQuestion";
    }

    private String resolveToolName(QueryLoopState state, String toolCallId) {
        if (state == null || state.getMessages() == null || toolCallId == null || toolCallId.isBlank()) {
            return "askUserQuestion";
        }

        for (ChatMessage message : state.getMessages()) {
            if (message instanceof dev.langchain4j.data.message.AiMessage aiMsg
                    && aiMsg.toolExecutionRequests() != null) {
                for (ToolExecutionRequest request : aiMsg.toolExecutionRequests()) {
                    if (toolCallId.equals(request.id())) {
                        return request.name();
                    }
                }
            }
        }
        return "askUserQuestion";
    }

    @Override
    public String formatAnswersForLLM(PendingSession pendingSession, List<UserAnswerDto> answers) {
        if (answers == null || answers.isEmpty()) {
            return "用户未提供回答";
        }

        StringBuilder sb = new StringBuilder("用户回答：\n");
        List<QuestionDto> questions = pendingSession.getQuestions();
        Map<String, QuestionDto> questionById = new HashMap<>();
        if (questions != null) {
            for (QuestionDto question : questions) {
                if (question.getQuestionId() != null) {
                    questionById.put(question.getQuestionId(), question);
                }
            }
        }

        for (int i = 0; i < answers.size(); i++) {
            UserAnswerDto answer = answers.get(i);
            QuestionDto question = questionById.get(answer.getQuestionId());
            if (question == null && questions != null && i < questions.size()) {
                question = questions.get(i);
            }
            String questionText = question != null ? question.getQuestionText() : "问题 " + (i + 1);
            sb.append("Q: ").append(questionText).append("\n");

            // 格式化回答
            String customInput = answer.getCustomInput();
            List<String> selectedOptionIds = answer.getSelectedOptionIds();

            if (Boolean.TRUE.equals(answer.getSkipped())) {
                sb.append("A: (跳过)\n\n");
            } else if (hasSelectedOptions(selectedOptionIds) || hasCustomInput(customInput)) {
                sb.append("A: ").append(formatAnswerText(question, selectedOptionIds, customInput)).append("\n\n");
            } else {
                sb.append("A: (未提供)\n\n");
            }

            if (answer.getNotes() != null && !answer.getNotes().isBlank()) {
                sb.append("备注: ").append(answer.getNotes()).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private boolean hasSelectedOptions(List<String> selectedOptionIds) {
        return selectedOptionIds != null && !selectedOptionIds.isEmpty();
    }

    private boolean hasCustomInput(String customInput) {
        return customInput != null && !customInput.isBlank();
    }

    private String formatAnswerText(QuestionDto question, List<String> selectedOptionIds, String customInput) {
        List<String> parts = new ArrayList<>();
        if (hasSelectedOptions(selectedOptionIds)) {
            parts.add(formatSelectedOptions(question, selectedOptionIds));
        }
        if (hasCustomInput(customInput)) {
            parts.add(customInput.trim());
        }
        return String.join("；", parts);
    }

    private String formatSelectedOptions(QuestionDto question, List<String> selectedOptionIds) {
        Map<String, String> optionTextById = new HashMap<>();
        if (question != null && question.getOptions() != null) {
            question.getOptions().forEach(option -> {
                if (option.getOptionId() != null && option.getDisplayText() != null && !option.getDisplayText().isBlank()) {
                    optionTextById.put(option.getOptionId(), option.getDisplayText());
                }
            });
        }

        return selectedOptionIds.stream()
                .map(optionId -> optionTextById.getOrDefault(optionId, optionId))
                .reduce((left, right) -> left + "、" + right)
                .orElse("(未提供)");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ChatMessage> deserializeMessages(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(stateJson, Map.class);
            Object messagesObj = map.get("messages");
            if (!(messagesObj instanceof List<?> messagesList)) {
                return Collections.emptyList();
            }

            List<ChatMessage> messages = new ArrayList<>();
            for (Object msgObj : messagesList) {
                if (msgObj instanceof Map<?, ?> msgMap) {
                    ChatMessage msg = deserializeMessage((Map<String, Object>) msgMap);
                    if (msg != null) {
                        messages.add(msg);
                    }
                }
            }
            return messages;
        } catch (JsonProcessingException e) {
            log.error("[PendingSessionService] 消息反序列化失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 反序列化单条消息
     */
    @SuppressWarnings("unchecked")
    private ChatMessage deserializeMessage(Map<String, Object> map) {
        String type = (String) map.get("type");
        if (type == null) {
            return null;
        }

        return switch (type) {
            case "USER" -> {
                String content = (String) map.get("content");
                yield dev.langchain4j.data.message.UserMessage.from(content != null ? content : "");
            }
            case "AI" -> {
                String content = (String) map.get("content");
                Object toolCallsObj = map.get("toolCalls");
                if (toolCallsObj instanceof List<?> toolCallsList && !toolCallsList.isEmpty()) {
                    List<dev.langchain4j.agent.tool.ToolExecutionRequest> toolRequests = new ArrayList<>();
                    for (Object tcObj : toolCallsList) {
                        if (tcObj instanceof Map<?, ?> tcMap) {
                            String id = (String) tcMap.get("id");
                            String name = (String) tcMap.get("name");
                            String args = (String) tcMap.get("arguments");
                            toolRequests.add(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                    .id(id)
                                    .name(name)
                                    .arguments(args != null ? args : "{}")
                                    .build());
                        }
                    }
                    yield dev.langchain4j.data.message.AiMessage.aiMessage(
                            content != null ? content : "",
                            toolRequests);
                }
                yield dev.langchain4j.data.message.AiMessage.aiMessage(content != null ? content : "");
            }
            case "SYSTEM" -> {
                String content = (String) map.get("content");
                yield dev.langchain4j.data.message.SystemMessage.from(content != null ? content : "");
            }
            case "TOOL_EXECUTION_RESULT" -> {
                String id = (String) map.get("id");
                String toolName = (String) map.get("toolName");
                String content = (String) map.get("content");
                yield dev.langchain4j.data.message.ToolExecutionResultMessage.from(
                        id, toolName, content != null ? content : "");
            }
            default -> null;
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> deserializeTaskPlan(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(stateJson, Map.class);
            Object taskPlanObj = map.get("taskPlan");
            if (taskPlanObj instanceof List<?> taskPlanList) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : taskPlanList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        result.add((Map<String, Object>) itemMap);
                    }
                }
                return result;
            }
            return Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.error("[PendingSessionService] 任务计划反序列化失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> deserializeSurfacedOpenVikingUris(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(stateJson, Map.class);
            Object surfacedObj = map.get("surfacedOpenVikingUris");
            if (!(surfacedObj instanceof Map<?, ?> surfacedMap)) {
                return Collections.emptyMap();
            }
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : surfacedMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return result;
        } catch (JsonProcessingException e) {
            log.error("[PendingSessionService] OpenViking 召回状态反序列化失败", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public UserProfile deserializeUserProfile(String userContextJson) {
        if (userContextJson == null || userContextJson.isBlank()) {
            return UserProfile.empty();
        }
        try {
            return objectMapper.readValue(userContextJson, UserProfile.class);
        } catch (JsonProcessingException e) {
            log.error("[PendingSessionService] UserProfile 反序列化失败", e);
            return UserProfile.empty();
        }
    }
}
