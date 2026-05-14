package com.msz.resume.ai.chat.api;

import com.msz.resume.ai.shared.response.Result;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.support.CurrentAccountResolver;
import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentityContextHolder;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingIdentityResolver;
import com.msz.resume.ai.chat.api.dto.ChatArtifact;
import com.msz.resume.ai.chat.api.dto.ChatResponse;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamContext;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamEventSink;
import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.InMemoryTimelineActionRecorder;
import com.msz.resume.ai.chat.runtime.trace.TimelineActionRecorder;
import com.msz.resume.ai.chat.runtime.trace.stream.TimelineActionRecorderFactory;
import com.msz.resume.ai.chat.runtime.trace.TimelineActionService;
import com.msz.resume.ai.chat.runtime.trace.TraceAgentDescriptor;
import com.msz.resume.ai.chat.runtime.trace.TraceService;
import com.msz.resume.ai.integrations.openviking.core.session.OpenVikingSessionGateway;
import com.msz.resume.ai.chat.session.service.SessionPersistenceService;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.chat.application.pending.PendingSessionService;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import com.msz.resume.ai.chat.tooling.dto.PendingSession;
import com.msz.resume.ai.chat.api.dto.UserAnswerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AskUserQuestion 恢复入口控制器。
 *
 * 当 LLM 调用 AskUserQuestion 工具时，会话被挂起到 Redis，
 * 用户回答后通过此控制器恢复执行。
 * 支持同步和 SSE 流式两种响应模式。
 */
@Slf4j
@RestController
@RequestMapping("/api/claude/chat/answer")
public class AnswerController {

    private final CompiledGraph<SessionState> queryEngineGraph;
    private final PendingSessionService pendingSessionService;
    private final SessionPersistenceService persistenceService;
    private final OpenVikingSessionGateway openVikingSessionGateway;
    private final CurrentAccountResolver currentAccountResolver;
    private final OpenVikingIdentityResolver openVikingIdentityResolver;
    private final ObjectMapper objectMapper;
    private final TraceService traceService;
    private final TimelineActionService timelineActionService;
    private final TimelineActionRecorderFactory timelineActionRecorderFactory;

    public AnswerController(CompiledGraph<SessionState> queryEngineGraph,
                            PendingSessionService pendingSessionService,
                            SessionPersistenceService persistenceService,
                            OpenVikingSessionGateway openVikingSessionGateway,
                            CurrentAccountResolver currentAccountResolver,
                            OpenVikingIdentityResolver openVikingIdentityResolver,
                            ObjectMapper objectMapper,
                            TraceService traceService,
                            TimelineActionService timelineActionService,
                            TimelineActionRecorderFactory timelineActionRecorderFactory) {
        this.queryEngineGraph = queryEngineGraph;
        this.pendingSessionService = pendingSessionService;
        this.persistenceService = persistenceService;
        this.openVikingSessionGateway = openVikingSessionGateway;
        this.currentAccountResolver = currentAccountResolver;
        this.openVikingIdentityResolver = openVikingIdentityResolver;
        this.objectMapper = objectMapper;
        this.traceService = traceService;
        this.timelineActionService = timelineActionService;
        this.timelineActionRecorderFactory = timelineActionRecorderFactory;
    }

    /** 同步方式恢复挂起会话，返回完整响应 */
    @PostMapping
    public Result<ChatResponse> answer(HttpServletRequest httpServletRequest,
                                       @RequestBody UserAnswerRequest request) {
        try {
            Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "chat/answer");
            PendingSession pendingSession = loadPendingSession(request);
            if (pendingSession == null || !belongsToCurrentUser(pendingSession, currentAccount)) {
                return Result.error("挂起会话不存在或已过期");
            }

            SessionState finalSessionState = resume(request, pendingSession, null, null);
            if (finalSessionState == null || finalSessionState.getInnerState() == null) {
                return Result.error("状态机执行失败：返回空结果");
            }

            QueryLoopState finalInnerState = finalSessionState.getInnerState();
            ChatResponse response = buildResponse(pendingSession.getSessionId(), finalSessionState, finalInnerState);
            deleteResolvedPending(pendingSession, response);
            if ("pending".equals(response.getStatus())) {
                persistenceService.persistTimelineActions(
                        pendingSession.getSessionId(),
                        extractOwnerUsername(pendingSession),
                        finalSessionState,
                        -1,
                        List.of(timelineActionService.pendingUserQuestionAction(
                                response.getPendingId(),
                                finalInnerState.getPendingToolCallId(),
                                response.getPendingQuestions(),
                                1L
                        ))
                );
            }
                persistIfComplete(
                        pendingSession.getSessionId(),
                        extractOwnerUsername(pendingSession),
                        finalSessionState,
                        finalInnerState,
                    response.getAiMessage(),
                    pendingSession,
                    List.of()
            );
            return Result.success(response);
        } catch (Exception e) {
            log.error("[AnswerController] 恢复挂起会话异常: pendingId={}, error={}",
                    request != null ? request.getPendingId() : null, e.getMessage(), e);
            return Result.error("执行异常: " + e.getMessage());
        }
    }

    /** SSE 流式方式恢复挂起会话，实时推送执行进度 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerStream(HttpServletRequest httpServletRequest,
                                   @RequestBody UserAnswerRequest request) {
        String pendingId = request != null ? request.getPendingId() : null;
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "chat/answer/stream");
        PendingSession resolvedPendingSession = loadPendingSession(request);
        if (resolvedPendingSession != null && !belongsToCurrentUser(resolvedPendingSession, currentAccount)) {
            resolvedPendingSession = null;
        }
        final PendingSession pendingSession = resolvedPendingSession;
        String sessionId = pendingSession != null ? pendingSession.getSessionId() : pendingId;
        SseEmitter emitter = new SseEmitter(180 * 1000L);
        InMemoryTimelineActionRecorder timelineActionRecorder = new InMemoryTimelineActionRecorder();
        TimelineActionRecorder recorder = timelineActionRecorderFactory.withTraceStream(sessionId, timelineActionRecorder);
        ChatStreamEventSink sink = new ChatStreamEventSink(emitter, objectMapper, sessionId, recorder);
        String runId = java.util.UUID.randomUUID().toString();
        ChatRunTraceContext traceContext = new ChatRunTraceContext(runId, sessionId, sink);

        CompletableFuture.runAsync(() -> {
            try {
                if (pendingSession == null) {
                    sink.error("PENDING_SESSION_NOT_FOUND", "挂起会话不存在或已过期");
                    sink.complete();
                    return;
                }

                ChatStreamContext.bindRun(sessionId, traceContext);
                sink.send("session_started", Map.of(
                        "status", "resuming",
                        "streaming", true,
                        "pendingId", pendingSession.getPendingId(),
                        "runId", runId
                ));
                traceService.startLlmRound(traceContext, TraceAgentDescriptor.mainAgent());

                SessionState finalSessionState = resume(request, pendingSession, sink, runId);
                if (finalSessionState == null || finalSessionState.getInnerState() == null) {
                    traceService.failMainLlmRound(traceContext);
                    ChatStreamContext.clear(sessionId);
                    sink.error("STATE_MACHINE_EMPTY_RESULT", "状态机执行失败：返回空结果");
                    sink.complete();
                    return;
                }

                QueryLoopState finalInnerState = finalSessionState.getInnerState();
                if (finalInnerState.getErrorMessage() != null && "terminate".equals(finalInnerState.getTransition())) {
                    traceService.failMainLlmRound(traceContext);
                    String errorType = finalInnerState.getErrorType() != null ? finalInnerState.getErrorType() : "LLM_ERROR";
                    sink.error(errorType, finalInnerState.getErrorMessage());
                    ChatStreamContext.clear(sessionId);
                    sink.complete();
                    return;
                }

                ChatResponse response = buildResponse(sessionId, finalSessionState, finalInnerState);
                if ("pending".equals(response.getStatus())) {
                    traceService.completeMainLlmRound(traceContext);
                    sink.send("ask_user_question", Map.of(
                            "pendingId", response.getPendingId(),
                            "toolCallId", finalInnerState.getPendingToolCallId() != null ? finalInnerState.getPendingToolCallId() : "",
                            "questions", response.getPendingQuestions()
                    ));
                    sink.send("pending", buildPendingPayload(response, finalSessionState, finalInnerState));
                    persistenceService.persistTimelineActions(
                            sessionId,
                            extractOwnerUsername(pendingSession),
                            finalSessionState,
                            -1,
                            timelineActionRecorder.snapshot()
                    );
                } else {
                    if (hasVisibleAssistantResult(response)) {
                        sink.send("message_done", buildMessageDonePayload(response));
                    }
                    traceService.completeMainLlmRound(traceContext);
                    sink.send("done", buildDonePayload(finalSessionState, finalInnerState));
                    deleteResolvedPending(pendingSession, response);
                }

                persistIfComplete(
                        sessionId,
                        extractOwnerUsername(pendingSession),
                        finalSessionState,
                        finalInnerState,
                        response.getAiMessage(),
                        pendingSession,
                        timelineActionRecorder.snapshot()
                );
                ChatStreamContext.clear(sessionId);
                sink.complete();
            } catch (Exception e) {
                log.error("[AnswerController] SSE 恢复挂起会话异常: pendingId={}, error={}", pendingId, e.getMessage(), e);
                traceService.failMainLlmRound(traceContext);
                ChatStreamContext.clear(sessionId);
                sink.error("ANSWER_STREAM_EXECUTION_ERROR", e.getMessage());
                sink.complete();
            }
        });

        return emitter;
    }

    /** 从 Redis 加载挂起的会话 */
    private PendingSession loadPendingSession(UserAnswerRequest request) {
        if (request == null || request.getPendingId() == null || request.getPendingId().isBlank()) {
            return null;
        }
        return pendingSessionService.get(request.getPendingId());
    }

    /** 删除已解决的挂起会话（非 pending 状态时） */
    private void deleteResolvedPending(PendingSession pendingSession, ChatResponse response) {
        if (pendingSession == null || "pending".equals(response.getStatus())) {
            return;
        }
        pendingSessionService.delete(pendingSession.getPendingId());
    }

    /** 恢复状态机执行：构造用户答案消息，重新运行状态机 */
    private SessionState resume(UserAnswerRequest request,
                                PendingSession pendingSession,
                                ChatStreamEventSink sink,
                                String runId) throws Exception {
        List<ChatMessage> messages = new ArrayList<>(pendingSessionService.deserializeMessages(pendingSession));
        String answerText = pendingSessionService.formatAnswersForLLM(pendingSession, request.getAnswers());
        messages.add(ToolExecutionResultMessage.from(
                pendingSession.getToolCallId(),
                pendingSessionService.resolveToolResultName(pendingSession),
                answerText
        ));

        UserProfile userContext = pendingSessionService.deserializeUserProfile(pendingSession.getUserContextJson());
        OpenVikingIdentity identity = resolveIdentity(userContext);
        QueryLoopState innerState = new QueryLoopState(buildInnerInput(pendingSession, messages, userContext, identity, runId));

        Map<String, Object> sessionInput = new HashMap<>();
        sessionInput.put(SessionState.SESSION_ID, pendingSession.getSessionId());
        sessionInput.put(SessionState.INNER_STATE, innerState);
        sessionInput.put(SessionState.USER_CONTEXT, userContext);
        sessionInput.put(SessionState.OPENVIKING_IDENTITY, identity);

        RunnableConfig runConfig = RunnableConfig.builder().threadId(pendingSession.getSessionId()).build();
        SessionState finalSessionState = null;
        List<Map<String, Object>> lastSentTaskPlan = new ArrayList<>();
        OpenVikingIdentityContextHolder.set(identity);
        try {
            for (NodeOutput<SessionState> output : queryEngineGraph.stream(sessionInput, runConfig)) {
                log.info("[AnswerController] 恢复执行步骤: node={}, sessionId={}", output.node(), pendingSession.getSessionId());
                finalSessionState = output.state();
                if (sink != null && sink.isClosed()) {
                    ChatStreamContext.clear(pendingSession.getSessionId());
                    return finalSessionState;
                }
                if (sink != null && finalSessionState != null && finalSessionState.getInnerState() != null) {
                    lastSentTaskPlan = sendTaskUpdateIfChanged(sink, finalSessionState.getInnerState(), lastSentTaskPlan);
                }
            }
            return finalSessionState;
        } finally {
            OpenVikingIdentityContextHolder.clear();
        }
    }

    /** 构建内层状态输入数据 */
    private Map<String, Object> buildInnerInput(
            PendingSession pendingSession,
            List<ChatMessage> messages,
            UserProfile userContext,
            OpenVikingIdentity identity,
            String runId) {
        Map<String, Object> innerInput = new HashMap<>();
        innerInput.put(QueryLoopState.MESSAGE_HISTORY, messages);
        innerInput.put(QueryLoopState.SESSION_ID, pendingSession.getSessionId());
        innerInput.put(QueryLoopState.TASK_PLAN, pendingSessionService.deserializeTaskPlan(pendingSession.getQueryLoopStateJson()));
        innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, pendingSessionService.deserializeSurfacedOpenVikingUris(pendingSession.getQueryLoopStateJson()));
        innerInput.put(QueryLoopState.USER_CONTEXT, userContext);
        innerInput.put(QueryLoopState.OPENVIKING_IDENTITY, identity != null ? identity : pendingSession.getOpenVikingIdentity());
        innerInput.put(QueryLoopState.TRACE_RUN_ID, runId);
        innerInput.put(QueryLoopState.TRACE_AGENT_ID, "main");
        innerInput.put(QueryLoopState.TRACE_AGENT_LABEL, "Main Agent");
        innerInput.put(QueryLoopState.TRACE_AGENT_SCOPE, "main");
        return innerInput;
    }

    /** 构建响应对象 */
    private ChatResponse buildResponse(String sessionId, SessionState finalSessionState, QueryLoopState finalInnerState) {
        List<ChatMessage> finalMessages = finalInnerState.getMessages();
        List<ChatArtifact> artifacts = ArtifactResponseExtractor.extractLatestArtifacts(finalMessages, objectMapper);
        String aiMessage = ArtifactResponseExtractor.stripPureArtifactText(
                extractLastAiMessage(finalMessages),
                artifacts,
                objectMapper
        );
        String mindmapData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "mindmap", objectMapper);
        if (mindmapData == null) {
            mindmapData = MindmapResponseExtractor.extractLatestMindmapData(finalMessages, objectMapper);
        }
        String questionnaireData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "questionnaire", objectMapper);
        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                .sessionId(sessionId)
                .aiMessage(aiMessage)
                .mindmapData(mindmapData)
                .questionnaireData(questionnaireData)
                .artifacts(artifacts)
                .tokenUsage(buildTokenUsage(finalSessionState, finalInnerState));

        String pendingId = finalInnerState.getPendingId();
        List<?> pendingQuestions = finalInnerState.getPendingQuestions();
        if (pendingId != null && pendingQuestions != null) {
            builder.status("pending")
                    .pendingId(pendingId)
                    .pendingQuestions(pendingQuestions)
                    .requiresUserInput(true);
        } else {
            builder.status("success");
        }

        List<Map<String, Object>> taskPlan = finalInnerState.getTaskPlan();
        if (taskPlan != null && !taskPlan.isEmpty()) {
            builder.taskPlan(taskPlan).taskProgress(buildTaskProgress(taskPlan));
        }
        return builder.build();
    }

    /** 持久化会话并同步到 OpenViking */
    private void persistIfComplete(String sessionId,
                                   String ownerUsername,
                                   SessionState finalSessionState,
                                   QueryLoopState finalInnerState,
                                   String aiMessage,
                                   PendingSession pendingSession,
                                   List<Map<String, Object>> timelineActions) {
        if (finalInnerState.getPendingId() != null) {
            log.info("[AnswerController] 跳过 pending 状态持久化，避免保存未配对工具调用: sessionId={}, pendingId={}",
                    sessionId, finalInnerState.getPendingId());
            return;
        }
        persistenceService.completeRound(
                sessionId,
                ownerUsername,
                finalSessionState,
                finalInnerState.getMessages(),
                timelineActions != null ? timelineActions : List.of()
        );
        if (finalInnerState.getPendingId() == null && aiMessage != null && !aiMessage.isBlank()) {
            OpenVikingIdentity identity = resolveIdentity(
                    pendingSessionService.deserializeUserProfile(pendingSession.getUserContextJson())
            );
            try {
                OpenVikingIdentityContextHolder.set(identity);
                openVikingSessionGateway.appendAssistantMessage(sessionId, aiMessage, identity);
            } catch (Exception e) {
                log.warn("[OpenViking] appendAssistantMessage 异常 (answer): sessionId={}, error={}", sessionId, e.getMessage());
            } finally {
                OpenVikingIdentityContextHolder.clear();
            }
        }
    }

    /** 构建 Token 用量对象 */
    private ChatResponse.TokenUsage buildTokenUsage(SessionState sessionState, QueryLoopState innerState) {
        Map<String, Object> payload = buildTokenUsagePayload(sessionState, innerState);
        return ChatResponse.TokenUsage.builder()
                .inputTokens((Integer) payload.get("inputTokens"))
                .outputTokens((Integer) payload.get("outputTokens"))
                .totalTokens((Integer) payload.get("totalTokens"))
                .build();
    }

    /** 构建 SSE done 事件载荷 */
    private Map<String, Object> buildDonePayload(SessionState finalSessionState, QueryLoopState finalInnerState) {
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> taskPlan = finalInnerState.getTaskPlan();
        payload.put("status", "success");
        payload.put("tokenUsage", buildTokenUsagePayload(finalSessionState, finalInnerState));
        payload.put("taskPlan", taskPlan != null ? taskPlan : List.of());
        payload.put("taskProgress", buildTaskProgress(taskPlan));
        return payload;
    }

    /** 构建 SSE message_done 事件载荷 */
    private Map<String, Object> buildMessageDonePayload(ChatResponse response) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("role", "assistant");
        payload.put("content", response.getAiMessage() != null ? response.getAiMessage() : "");
        payload.put("streaming", true);
        payload.put("artifacts", response.getArtifacts() != null ? response.getArtifacts() : List.of());
        if (response.getMindmapData() != null && !response.getMindmapData().isBlank()) {
            payload.put("mindmapData", response.getMindmapData());
        }
        if (response.getQuestionnaireData() != null && !response.getQuestionnaireData().isBlank()) {
            payload.put("questionnaireData", response.getQuestionnaireData());
        }
        return payload;
    }

    private boolean hasVisibleAssistantResult(ChatResponse response) {
        if (response == null) {
            return false;
        }
        return (response.getAiMessage() != null && !response.getAiMessage().isBlank())
                || (response.getMindmapData() != null && !response.getMindmapData().isBlank())
                || (response.getQuestionnaireData() != null && !response.getQuestionnaireData().isBlank())
                || (response.getArtifacts() != null && !response.getArtifacts().isEmpty());
    }

    /** 构建 SSE pending 事件载荷 */
    private Map<String, Object> buildPendingPayload(ChatResponse response, SessionState finalSessionState, QueryLoopState finalInnerState) {
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> taskPlan = finalInnerState.getTaskPlan();
        payload.put("status", "pending");
        payload.put("pendingId", response.getPendingId());
        payload.put("toolCallId", finalInnerState.getPendingToolCallId() != null ? finalInnerState.getPendingToolCallId() : "");
        payload.put("questions", response.getPendingQuestions());
        payload.put("tokenUsage", buildTokenUsagePayload(finalSessionState, finalInnerState));
        payload.put("taskPlan", taskPlan != null ? taskPlan : List.of());
        payload.put("taskProgress", buildTaskProgress(taskPlan));
        return payload;
    }

    /** 构建 Token 用量载荷（含子 Agent 累加） */
    private Map<String, Object> buildTokenUsagePayload(SessionState sessionState, QueryLoopState innerState) {
        int subAgentInputTokens = 0;
        int subAgentOutputTokens = 0;
        List<Map<String, Integer>> subAgentTokenAccumulator = innerState.getSubAgentTokenAccumulator();
        if (subAgentTokenAccumulator != null && !subAgentTokenAccumulator.isEmpty()) {
            for (Map<String, Integer> entry : subAgentTokenAccumulator) {
                subAgentInputTokens += entry.getOrDefault("inputTokens", 0);
                subAgentOutputTokens += entry.getOrDefault("outputTokens", 0);
            }
        }

        int inputTokens = sessionState.getTotalInputTokens() + subAgentInputTokens;
        int outputTokens = sessionState.getTotalOutputTokens() + subAgentOutputTokens;
        return Map.of(
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "totalTokens", inputTokens + outputTokens
        );
    }

    /** 构建任务进度统计 */
    private Map<String, Integer> buildTaskProgress(List<Map<String, Object>> taskPlan) {
        Map<String, Integer> progress = new HashMap<>();
        progress.put("total", taskPlan != null ? taskPlan.size() : 0);
        progress.put("pending", 0);
        progress.put("in_progress", 0);
        progress.put("completed", 0);
        progress.put("skipped", 0);

        if (taskPlan != null) {
            for (Map<String, Object> task : taskPlan) {
                String status = String.valueOf(task.getOrDefault("status", "pending"));
                progress.merge(status, 1, Integer::sum);
            }
        }
        return progress;
    }

    /** 任务计划变化时发送 SSE task_update 事件 */
    private List<Map<String, Object>> sendTaskUpdateIfChanged(
            ChatStreamEventSink sink,
            QueryLoopState innerState,
            List<Map<String, Object>> lastSentTaskPlan) throws Exception {
        List<Map<String, Object>> currentTaskPlan = innerState.getTaskPlan();
        if (currentTaskPlan == null || currentTaskPlan.equals(lastSentTaskPlan)) {
            return lastSentTaskPlan;
        }

        sink.send("task_update", Map.of(
                "taskPlan", currentTaskPlan,
                "taskProgress", buildTaskProgress(currentTaskPlan)
        ));
        return new ArrayList<>(currentTaskPlan);
    }

    /** 从消息列表中提取最后一条有内容的 AI 消息 */
    private String extractLastAiMessage(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
                String text = aiMsg.text();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private OpenVikingIdentity resolveIdentity(UserProfile userContext) {
        if (userContext == null || userContext.username() == null || userContext.username().isBlank()) {
            throw new IllegalArgumentException("挂起会话缺少用户身份，无法恢复 OpenViking 上下文");
        }
        return openVikingIdentityResolver.resolve(
                new com.msz.resume.ai.auth.entity.Account(null, userContext.username(), null, null, null, null, null)
        );
    }

    private boolean belongsToCurrentUser(PendingSession pendingSession, Account currentAccount) {
        return pendingSession != null
                && currentAccount != null
                && currentAccount.getUsername() != null
                && currentAccount.getUsername().equals(extractOwnerUsername(pendingSession));
    }

    private String extractOwnerUsername(PendingSession pendingSession) {
        UserProfile userContext = pendingSessionService.deserializeUserProfile(
                pendingSession != null ? pendingSession.getUserContextJson() : null
        );
        return userContext.username();
    }
}
