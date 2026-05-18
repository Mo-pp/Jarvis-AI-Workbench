package com.msz.resume.ai.chat.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.support.CurrentAccountResolver;
import com.msz.resume.ai.chat.api.dto.ChatArtifact;
import com.msz.resume.ai.chat.api.dto.ChatRequest;
import com.msz.resume.ai.chat.api.dto.ChatResponse;
import com.msz.resume.ai.chat.api.dto.ChatStreamEvent;
import com.msz.resume.ai.chat.api.dto.SessionPinRequest;
import com.msz.resume.ai.chat.api.dto.SessionRenameRequest;
import com.msz.resume.ai.chat.api.dto.SessionSummaryResponse;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamEventSink;
import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamContext;
import com.msz.resume.ai.chat.runtime.trace.InMemoryTimelineActionRecorder;
import com.msz.resume.ai.chat.runtime.trace.TimelineActionRecorder;
import com.msz.resume.ai.chat.runtime.trace.stream.TraceReplayService;
import com.msz.resume.ai.chat.runtime.trace.stream.TimelineActionRecorderFactory;
import com.msz.resume.ai.chat.runtime.trace.TraceAgentDescriptor;
import com.msz.resume.ai.chat.runtime.trace.TraceService;
import com.msz.resume.ai.file.service.FileStorageService;
import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentityContextHolder;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingIdentityResolver;
import com.msz.resume.ai.integrations.openviking.core.session.OpenVikingSessionGateway;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.runtime.state.SessionState;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import com.msz.resume.ai.shared.response.Result;
import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import com.msz.resume.ai.chat.tooling.AskUserQuestionParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.msz.resume.ai.chat.session.model.SessionSnapshot;
import com.msz.resume.ai.chat.session.model.SessionSummary;
import com.msz.resume.ai.chat.session.model.HistoryMessage;
import com.msz.resume.ai.chat.session.service.SessionPersistenceService;


import java.util.*;
import java.util.concurrent.CompletableFuture;
/**
 * Claude 对话主控制器。
 *
 * 作用：承接聊天主入口、SSE 流式输出、会话历史读取和 Trace 回放请求，
 * 是前端进入 Query Loop 与 Trace 主链的第一站。
 * 可以把它理解成“总前台”，用户每次发问、追历史、补拉 trace，基本都先经过这里。
 *
 * 代码逻辑：
 * 1. 同步接口负责恢复/创建会话，组装 SessionState 后驱动外层图执行
 * 2. SSE 接口负责创建 ChatRunTraceContext、绑定 ChatStreamContext，并实时推送事件
 * 3. 流式执行期间把 timeline action 同步写入内存和 Trace Stream，结束后统一持久化
 * 4. 历史/回放接口负责把消息记录和 trace 事件重新整理给前端恢复展示
 */

@Slf4j
@RestController
@RequestMapping("/api/claude")
public class ClaudeController {
    /**
     * 注入编译好的外层图
     * 这是状态机的入口，通过它可以执行整个对话流程
     */

    private final CompiledGraph<SessionState> queryEngineGraph;
    private final SessionPersistenceService persistenceService;
    private final OpenVikingSessionGateway openVikingSessionGateway;
    private final CurrentAccountResolver currentAccountResolver;
    private final OpenVikingIdentityResolver openVikingIdentityResolver;
    private final ObjectMapper objectMapper;
    private final AskUserQuestionParser askUserQuestionParser;
    private final FileStorageService fileStorageService;
    private final TraceService traceService;
    private final TimelineActionRecorderFactory timelineActionRecorderFactory;
    private final TraceReplayService traceReplayService;

    /** 注入聊天主流程和 trace 相关依赖，组装控制器需要的全链路能力。 */
    public ClaudeController(CompiledGraph<SessionState> queryEngineGraph,
                            SessionPersistenceService persistenceService,
                            OpenVikingSessionGateway openVikingSessionGateway,
                            CurrentAccountResolver currentAccountResolver,
                            OpenVikingIdentityResolver openVikingIdentityResolver,
                            ObjectMapper objectMapper,
                            AskUserQuestionParser askUserQuestionParser,
                            FileStorageService fileStorageService,
                            TraceService traceService,
                            TimelineActionRecorderFactory timelineActionRecorderFactory,
                            TraceReplayService traceReplayService) {
        this.queryEngineGraph = queryEngineGraph;
        this.persistenceService = persistenceService;
        this.openVikingSessionGateway = openVikingSessionGateway;
        this.currentAccountResolver = currentAccountResolver;
        this.openVikingIdentityResolver = openVikingIdentityResolver;
        this.objectMapper = objectMapper;
        this.askUserQuestionParser = askUserQuestionParser;
        this.fileStorageService = fileStorageService;
        this.traceService = traceService;
        this.timelineActionRecorderFactory = timelineActionRecorderFactory;
        this.traceReplayService = traceReplayService;
    }




    /**
     * 对话接口（同步响应）
     *
     * 执行流程：
     * 1. 解析请求参数
     * 2. 构造SessionState输入
     * 3. 调用状态机执行
     * 4. 提取结果返回
     *
     * @param request 对话请求
     * @return 对话响应
     */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(HttpServletRequest httpServletRequest,
                                     @RequestBody ChatRequest request) {
        try {
            Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "chat");
            OpenVikingIdentity identity = openVikingIdentityResolver.resolve(currentAccount);
            OpenVikingIdentityContextHolder.set(identity);

            // ========== 1. 处理会话ID ==========
            // 如果前端没传sessionId，就生成一个新的
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
                log.info("[新会话] 生成sessionId: {}", sessionId);
            } else {
                log.info("[会话恢复] 使用已有sessionId: {}", sessionId);
            }

            // ========== 2. 构造内层状态输入 ==========
            // 内层状态包含消息历史
            QueryLoopState innerState;

            // 尝试从数据库恢复会话
            SessionSnapshot snapshot = persistenceService.restoreSession(sessionId, currentAccount.getUsername());

            if (snapshot != null) {
                // 会话恢复：从快照中提取消息历史，构造内层状态
                Map<String, Object> innerInput = new HashMap<>();
                innerInput.put(QueryLoopState.MESSAGE_HISTORY, snapshot.messages());
                innerInput.put(QueryLoopState.TASK_PLAN, snapshot.state().getInnerState().getTaskPlan());
                innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, snapshot.state().getInnerState().getSurfacedOpenVikingUris());
                innerInput.put(QueryLoopState.OPENVIKING_IDENTITY, identity);
                innerState = new QueryLoopState(innerInput);
                log.info("[会话恢复] 从数据库恢复历史消息数: {}", snapshot.messages().size());
            } else {
                // 新会话：创建空的内层状态
                innerState = new QueryLoopState(new HashMap<>());
                log.info("[新会话] sessionId: {}", sessionId);
            }

            // ========== 3. 添加用户消息到内层状态 ==========
            // 将用户输入包装成UserMessage，准备传给大模型
            // 这里创建新的输入Map，包含用户消息
            Map<String, Object> innerInput = new HashMap<>();
            List<ChatMessage> messages = new ArrayList<>(innerState.getMessages());// 获取之前的内层状态的聊天消息

            // ========== 3.1 处理关联的文件 ==========
            // 如果请求中包含 fileId，获取文件内容并注入到消息中
            String userMessage = request.getUserMessage();
            String fileId = request.getFileId();
            if (fileId != null && !fileId.isBlank()) {
                userMessage = injectFileContent(fileId, userMessage);
            }

            messages.add(UserMessage.from(userMessage));

            // ========== 3.1 追加用户消息到 OpenViking Session（最佳努力） ==========
            try {
                openVikingSessionGateway.appendUserMessage(sessionId, request.getUserMessage(), identity);
            } catch (Exception e) {
                log.warn("[OpenViking] appendUserMessage 异常: sessionId={}, error={}", sessionId, e.getMessage());
            }

            // 创建带有用户消息的新内层状态
            innerInput.put(QueryLoopState.MESSAGE_HISTORY, messages);
            innerInput.put(QueryLoopState.TASK_PLAN, innerState.getTaskPlan());
            innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, innerState.getSurfacedOpenVikingUris());
            innerInput.put(QueryLoopState.OPENVIKING_IDENTITY, identity);
            QueryLoopState newInnerState = new QueryLoopState(innerInput);


            // ========== 4. 构造用户上下文 ==========
            // 从当前登录账户构造 UserProfile，避免信任前端传入身份
            UserProfile userContext = buildUserProfile(currentAccount, identity, request.getLanguage(), request.getOutputStyle());

            // ========== 5. 构造外层状态输入 ==========
            // 外层状态包含会话ID、内层状态、用户上下文
            Map<String, Object> sessionInput = new HashMap<>();
            sessionInput.put(SessionState.SESSION_ID, sessionId);
            sessionInput.put(SessionState.INNER_STATE, newInnerState);
            sessionInput.put(SessionState.USER_CONTEXT, userContext);
            sessionInput.put(SessionState.OPENVIKING_IDENTITY, identity);

            // ========== 5. 构造运行配置 ==========
            // threadId用于标识这次执行，支持持久化时可用于恢复
            RunnableConfig runConfig = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // ========== 6. 执行状态机 ==========
            // 调用外层图，开始执行对话流程
            // 流程：session_init → run_inner_loop → usage_stat → (循环判断)
            log.info("[开始执行] sessionId: {}, 用户消息: {}", sessionId, request.getUserMessage());
            SessionState finalSessionState = null;
            for (NodeOutput<SessionState> output : queryEngineGraph.stream(sessionInput, runConfig)) {
                log.info("[执行步骤] 节点: {}, 会话ID: {}", output.node(), sessionId);
                finalSessionState = output.state();
            }

            // ========== 7. 提取结果 ==========
            if (finalSessionState == null) {
                log.error("[执行失败] 状态机返回空结果");
                return Result.error("状态机执行失败：返回空结果");
            }
            // 获取内层最终状态
            QueryLoopState finalInnerState = finalSessionState.getInnerState();
            if (finalInnerState == null) {
                log.error("[执行失败] 内层状态为空");
                return Result.error("状态机执行失败：内层状态为空");
            }

            // ========== 8. 提取AI回复 ==========
            // 从消息列表中找到最后一条AI消息  extractLastAiMessage可以返回AI消息

            List<ChatMessage> finalMessages = finalInnerState.getMessages();
            List<ChatArtifact> artifacts = ArtifactResponseExtractor.extractLatestArtifacts(finalMessages, objectMapper);
            String aiMessageContent = ArtifactResponseExtractor.stripPureArtifactText(
                    extractLastAiMessage(finalMessages),
                    artifacts,
                    objectMapper
            );
            String mindmapData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "mindmap", objectMapper);
            if (mindmapData == null) {
                mindmapData = MindmapResponseExtractor.extractLatestMindmapData(finalMessages, objectMapper);
            }
            String questionnaireData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "questionnaire", objectMapper);

            // ========== 8.1 追加助手消息到 OpenViking Session（最佳努力） ==========
            if (aiMessageContent != null && !aiMessageContent.isBlank()) {
                try {
                    openVikingSessionGateway.appendAssistantMessage(sessionId, aiMessageContent, identity);
                } catch (Exception e) {
                    log.warn("[OpenViking] appendAssistantMessage 异常: sessionId={}, error={}", sessionId, e.getMessage());
                }
            }

            // ========== 9. 构造响应 ==========
            // 聚合子Agent的Token用量到响应中
            int subAgentInputTokens = 0;
            int subAgentOutputTokens = 0;
            List<Map<String, Integer>> subAgentTokenAccumulator = finalInnerState.getSubAgentTokenAccumulator();
            if (subAgentTokenAccumulator != null && !subAgentTokenAccumulator.isEmpty()) {
                for (Map<String, Integer> entry : subAgentTokenAccumulator) {
                    subAgentInputTokens += entry.getOrDefault("inputTokens", 0);
                    subAgentOutputTokens += entry.getOrDefault("outputTokens", 0);
                }
            }

            ChatResponse.ChatResponseBuilder responseBuilder = ChatResponse.builder()
                    .sessionId(sessionId)
                    .aiMessage(aiMessageContent)
                    .mindmapData(mindmapData)
                    .questionnaireData(questionnaireData)
                    .artifacts(artifacts)
                    .tokenUsage(ChatResponse.TokenUsage.builder()
                            .inputTokens(finalSessionState.getTotalInputTokens() + subAgentInputTokens)
                            .outputTokens(finalSessionState.getTotalOutputTokens() + subAgentOutputTokens)
                            .totalTokens(finalSessionState.getTotalInputTokens() + finalSessionState.getTotalOutputTokens()
                                    + subAgentInputTokens + subAgentOutputTokens)
                            .build());

            // ========== 10. 持久化会话 ==========
            responseBuilder.status("success");
            persistenceService.completeRound(
                    sessionId,
                    currentAccount.getUsername(),
                    finalSessionState,
                    finalInnerState.getMessages()
            );

            // ========== 11. 设置任务规划进度 ==========
            List<Map<String, Object>> taskPlan = finalInnerState.getTaskPlan();
            if (taskPlan != null && !taskPlan.isEmpty()) {
                responseBuilder.taskPlan(taskPlan);
                // 计算任务进度统计
                Map<String, Integer> progress = new HashMap<>();
                progress.put("total", taskPlan.size());
                progress.put("pending", 0);
                progress.put("in_progress", 0);
                progress.put("completed", 0);
                progress.put("skipped", 0);
                for (Map<String, Object> task : taskPlan) {
                    String status = String.valueOf(task.getOrDefault("status", "pending"));
                    progress.merge(status, 1, Integer::sum);
                }
                responseBuilder.taskProgress(progress);
            }

            ChatResponse response = responseBuilder.build();
            log.info("[执行完成] sessionId: {}, AI回复长度: {}, Token用量: input={}, output={}, 子Agent: input={}, output={}",
                    sessionId,
                    aiMessageContent != null ? aiMessageContent.length() : 0,
                    finalSessionState.getTotalInputTokens(),
                    finalSessionState.getTotalOutputTokens(),
                    subAgentInputTokens,
                    subAgentOutputTokens);


            return Result.success(response);

        } catch (Exception e) {
            log.error("[执行异常] {}", e.getMessage(), e);
            return Result.error("执行异常: " + e.getMessage());
        } finally {
            OpenVikingIdentityContextHolder.clear();
        }
    }

    /**
     * 流式对话接口（SSE 官方标准版本）
     *
     * <p>P1：输出结构化 SSE 事件，但仍使用现有阻塞式状态机执行路径。
     * 真正 token delta streaming 在后续阶段接入。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String sessionId,
                                 @RequestParam String userMessage,
                                 @RequestParam(required = false) String language,
                                 @RequestParam(required = false) String outputStyle,
                                 @RequestParam(required = false) String fileId,
                                 HttpServletRequest httpServletRequest) {

        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "chat/stream");
        OpenVikingIdentity identity = openVikingIdentityResolver.resolve(currentAccount);
        UserProfile userContext = buildUserProfile(currentAccount, identity, language, outputStyle);

        SseEmitter emitter = new SseEmitter(180 * 1000L);
        InMemoryTimelineActionRecorder timelineActionRecorder = new InMemoryTimelineActionRecorder();
        TimelineActionRecorder recorder = timelineActionRecorderFactory.withTraceStream(sessionId, timelineActionRecorder);
        ChatStreamEventSink sink = new ChatStreamEventSink(emitter, objectMapper, sessionId, recorder);
        String runId = UUID.randomUUID().toString();
        ChatRunTraceContext traceContext = new ChatRunTraceContext(runId, sessionId, sink);

        CompletableFuture.runAsync(() -> {
            SessionState finalSessionState = null;
            try {
                OpenVikingIdentityContextHolder.set(identity);
                ChatStreamContext.bindRun(sessionId, traceContext);
                sendBestEffort(sessionId, sink, ignored ->
                        sink.send("session_started", Map.of(
                                "status", "started",
                                "streaming", true,
                                "runId", runId
                        )));
                traceService.startLlmRound(traceContext, TraceAgentDescriptor.mainAgent());

                // 从数据库恢复会话，构造输入数据
                SessionSnapshot snapshot = persistenceService.restoreSession(sessionId, currentAccount.getUsername());
                QueryLoopState innerState;
                if (snapshot != null) {
                    Map<String, Object> innerInput = new HashMap<>();
                    innerInput.put(QueryLoopState.MESSAGE_HISTORY, snapshot.messages());
                    innerInput.put(QueryLoopState.TASK_PLAN, snapshot.state().getInnerState().getTaskPlan());
                    innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, snapshot.state().getInnerState().getSurfacedOpenVikingUris());
                    innerInput.put(QueryLoopState.OPENVIKING_IDENTITY, identity);
                    innerInput.put(QueryLoopState.TRACE_RUN_ID, runId);
                    innerInput.put(QueryLoopState.TRACE_AGENT_ID, "main");
                    innerInput.put(QueryLoopState.TRACE_AGENT_LABEL, "Main Agent");
                    innerInput.put(QueryLoopState.TRACE_AGENT_SCOPE, "main");
                    innerState = new QueryLoopState(innerInput);
                } else {
                    Map<String, Object> freshInput = new HashMap<>();
                    freshInput.put(QueryLoopState.TRACE_RUN_ID, runId);
                    freshInput.put(QueryLoopState.TRACE_AGENT_ID, "main");
                    freshInput.put(QueryLoopState.TRACE_AGENT_LABEL, "Main Agent");
                    freshInput.put(QueryLoopState.TRACE_AGENT_SCOPE, "main");
                    innerState = new QueryLoopState(freshInput);
                }

                // 处理关联的文件
                String finalUserMessage = userMessage;
                if (fileId != null && !fileId.isBlank()) {
                    finalUserMessage = injectFileContent(fileId, userMessage);
                }

                Map<String, Object> innerInput = new HashMap<>();
                List<ChatMessage> messages = new ArrayList<>(innerState.getMessages());
                messages.add(UserMessage.from(finalUserMessage));
                innerInput.put(QueryLoopState.MESSAGE_HISTORY, messages);
                innerInput.put(QueryLoopState.TASK_PLAN, innerState.getTaskPlan());
                innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, innerState.getSurfacedOpenVikingUris());
                innerInput.put(QueryLoopState.OPENVIKING_IDENTITY, identity);
                innerInput.put(QueryLoopState.TRACE_RUN_ID, runId);
                innerInput.put(QueryLoopState.TRACE_AGENT_ID, innerState.getTraceAgentId());
                innerInput.put(QueryLoopState.TRACE_AGENT_LABEL, innerState.getTraceAgentLabel());
                innerInput.put(QueryLoopState.TRACE_AGENT_SCOPE, innerState.getTraceAgentScope());
                QueryLoopState newInnerState = new QueryLoopState(innerInput);

                // ========== 追加用户消息到 OpenViking Session（最佳努力） ==========
                try {
                    openVikingSessionGateway.appendUserMessage(sessionId, userMessage, identity);
                } catch (Exception e) {
                    log.warn("[OpenViking] appendUserMessage 异常 (SSE): sessionId={}, error={}", sessionId, e.getMessage());
                }

                Map<String, Object> sessionInput = new HashMap<>();
                sessionInput.put(SessionState.SESSION_ID, sessionId);
                sessionInput.put(SessionState.INNER_STATE, newInnerState);
                sessionInput.put(SessionState.USER_CONTEXT, userContext);
                sessionInput.put(SessionState.OPENVIKING_IDENTITY, identity);
                RunnableConfig runConfig = RunnableConfig.builder().threadId(sessionId).build();

                List<Map<String, Object>> lastSentTaskPlan = Collections.emptyList();
                for (NodeOutput<SessionState> output : queryEngineGraph.stream(sessionInput, runConfig)) {
                    log.info("[SSE执行步骤] 节点: {}, 会话ID: {}", output.node(), sessionId);
                    finalSessionState = output.state();
                    if (!sink.isClosed() && finalSessionState != null && finalSessionState.getInnerState() != null) {
                        lastSentTaskPlan = sendTaskUpdateIfChanged(sink, finalSessionState.getInnerState(), lastSentTaskPlan);
                    }
                }

                if (finalSessionState == null || finalSessionState.getInnerState() == null) {
                    traceService.failMainLlmRound(traceContext);
                    ChatStreamContext.clear(sessionId);
                    if (!sink.isClosed()) {
                        sink.error("STATE_MACHINE_EMPTY_RESULT", "状态机执行失败：返回空结果");
                        sink.complete();
                    }
                    return;
                }

                QueryLoopState finalInnerState = finalSessionState.getInnerState();
                if (finalInnerState.getErrorMessage() != null && "terminate".equals(finalInnerState.getTransition())) {
                    traceService.failMainLlmRound(traceContext);
                    String errorType = finalInnerState.getErrorType() != null ? finalInnerState.getErrorType() : "LLM_ERROR";
                    sendBestEffort(sessionId, sink, ignored ->
                            sink.error(errorType, finalInnerState.getErrorMessage()));
                    ChatStreamContext.clear(sessionId);
                    if (!sink.isClosed()) {
                        sink.complete();
                    }
                    return;
                }

                List<ChatMessage> finalMessages = finalInnerState.getMessages();
                List<ChatArtifact> artifacts = ArtifactResponseExtractor.extractLatestArtifacts(finalMessages, objectMapper);
                String lastAiContent = ArtifactResponseExtractor.stripPureArtifactText(
                        extractLastAiMessage(finalMessages),
                        artifacts,
                        objectMapper
                );
                String mindmapData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "mindmap", objectMapper);
                if (mindmapData == null) {
                    mindmapData = MindmapResponseExtractor.extractLatestMindmapData(finalMessages, objectMapper);
                }
                String questionnaireData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "questionnaire", objectMapper);
                if (hasVisibleAssistantResult(lastAiContent, mindmapData, questionnaireData, artifacts)) {
                    Map<String, Object> messageDonePayload = buildMessageDonePayload(
                            lastAiContent,
                            mindmapData,
                            questionnaireData,
                            artifacts
                    );
                    sendBestEffort(sessionId, sink, ignored ->
                            sink.send("message_done", messageDonePayload));

                    // ========== 追加助手消息到 OpenViking Session（最佳努力） ==========
                    if (lastAiContent != null && !lastAiContent.isBlank()) {
                        try {
                            openVikingSessionGateway.appendAssistantMessage(sessionId, lastAiContent, identity);
                        } catch (Exception e) {
                            log.warn("[OpenViking] appendAssistantMessage 异常 (SSE): sessionId={}, error={}", sessionId, e.getMessage());
                        }
                    }
                }

                persistenceService.completeRound(
                        sessionId,
                        currentAccount.getUsername(),
                        finalSessionState,
                        finalInnerState.getMessages(),
                        timelineActionRecorder.snapshot()
                );

                Map<String, Object> donePayload = buildDonePayload(finalSessionState, finalInnerState);
                traceService.completeMainLlmRound(traceContext);
                sendBestEffort(sessionId, sink, ignored ->
                        sink.send("done", donePayload));
                ChatStreamContext.clear(sessionId);
                if (!sink.isClosed()) {
                    sink.complete();
                }

            } catch (Exception e) {
                log.error("[SSE执行异常] sessionId={}, error={}", sessionId, e.getMessage(), e);
                traceService.failMainLlmRound(traceContext);
                ChatStreamContext.clear(sessionId);
                if (!sink.isClosed()) {
                    sink.error("STREAM_EXECUTION_ERROR", e.getMessage());
                    sink.complete();
                }
            } finally {
                OpenVikingIdentityContextHolder.clear();
            }
        });

        return emitter;
    }


    /**
     * 获取会话历史
     *
     * 用于前端恢复对话时，获取之前的聊天记录
     *
     * @param sessionId 会话ID
     * @return 会话历史
     */
    @GetMapping("/session/{sessionId}/history")
    public Result<List<Object>> getSessionHistory(HttpServletRequest httpServletRequest,
                                                  @PathVariable String sessionId) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "session/history");
        SessionSnapshot snapshot = persistenceService.restoreSession(sessionId, currentAccount.getUsername());

        if (snapshot == null) {
            return Result.error("会话不存在或已过期");
        }
        // 返回消息历史
        List<Object> history = new ArrayList<>();
        if (snapshot.historyMessages() != null && !snapshot.historyMessages().isEmpty()) {
            for (HistoryMessage historyMessage : snapshot.historyMessages()) {
                history.add(formatHistoryMessage(historyMessage));
            }
        } else {
            for (ChatMessage msg : snapshot.messages()) {
                history.add(formatMessage(msg));
            }
        }
        return Result.success(history);
    }

    @GetMapping("/session/{sessionId}/trace/replay")
    /** 按 sequence 补拉某次会话的 trace 事件，常用于断线续传和历史回放。 */
    public Result<List<ChatStreamEvent>> replaySessionTrace(HttpServletRequest httpServletRequest,
                                                            @PathVariable String sessionId,
                                                            @RequestParam(required = false) String runId,
                                                            @RequestParam(defaultValue = "0") long lastSequence,
                                                            @RequestParam(defaultValue = "50") int count) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "session/trace/replay");
        SessionSnapshot snapshot = persistenceService.restoreSession(sessionId, currentAccount.getUsername());
        if (snapshot == null) {
            return Result.error("会话不存在或已过期");
        }
        return Result.success(traceReplayService.replaySince(sessionId, runId, lastSequence, Math.min(Math.max(count, 1), 200)));
    }


    /**
     * 删除会话
     *
     * 软删除会话（status=closed），已关闭的会话无法继续对话
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Boolean> deleteSession(HttpServletRequest httpServletRequest,
                                         @PathVariable String sessionId) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "session");
        SessionSnapshot snapshot = persistenceService.restoreSession(sessionId, currentAccount.getUsername());
        if (snapshot == null) {
            return Result.error("会话不存在");
        }
        persistenceService.closeSession(sessionId, currentAccount.getUsername());
        log.info("[删除会话] sessionId: {}", sessionId);
        return Result.success(true);
    }

    @PatchMapping("/session/{sessionId}/title")
    /** 修改会话标题，让会话列表更容易识别。 */
    public Result<SessionSummaryResponse> renameSession(HttpServletRequest httpServletRequest,
                                                        @PathVariable String sessionId,
                                                        @RequestBody SessionRenameRequest request) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "session/title");
        SessionSummary summary = persistenceService.renameSession(
                sessionId,
                currentAccount.getUsername(),
                request != null ? request.getTitle() : null
        );
        if (summary == null) {
            return Result.error("session not found");
        }
        log.info("[rename session] sessionId={}, title={}", sessionId, summary.title());
        return Result.success(toSessionSummaryResponse(summary));
    }

    @PatchMapping("/session/{sessionId}/pin")
    /** 设置会话是否置顶，方便前端按优先级展示。 */
    public Result<SessionSummaryResponse> pinSession(HttpServletRequest httpServletRequest,
                                                     @PathVariable String sessionId,
                                                     @RequestBody SessionPinRequest request) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "session/pin");
        boolean pinned = request != null && Boolean.TRUE.equals(request.getPinned());
        SessionSummary summary = persistenceService.pinSession(sessionId, currentAccount.getUsername(), pinned);
        if (summary == null) {
            return Result.error("session not found");
        }
        log.info("[pin session] sessionId={}, pinned={}", sessionId, pinned);
        return Result.success(toSessionSummaryResponse(summary));
    }

    /**
     * 获取所有活跃会话
     *
     * 用于管理后台查看当前有多少活跃会话
     *
     * @return 会话ID列表
     */
    @GetMapping("/sessions")
    public Result<List<SessionSummaryResponse>> getAllSessions(HttpServletRequest httpServletRequest) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "sessions");
        List<SessionSummaryResponse> sessions = persistenceService.listActiveSessions(currentAccount.getUsername()).stream()
                .map(this::toSessionSummaryResponse)
                .toList();
        return Result.success(sessions);
    }

    /** 把会话摘要模型转换成接口层响应对象。 */
    private SessionSummaryResponse toSessionSummaryResponse(SessionSummary summary) {
        return SessionSummaryResponse.builder()
                .sessionId(summary.sessionId())
                .title(summary.title())
                .pinned(summary.pinned())
                .createdAt(summary.createdAt())
                .lastActiveAt(summary.lastActiveAt())
                .build();
    }





// =============================================== 辅助方法（修复版 - 旧版 LangChain4j） ==================================================

    /**
     * 从消息列表中提取最后一条AI消息
     *
     * @param messages 消息列表
     * @return AI消息内容，如果没有则返回null
     */
    private String extractLastAiMessage(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        // 从后往前找最后一条有实质内容的AI消息
        // 跳过空文本的AiMessage（Nudge催促后LLM可能返回空文本）
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof AiMessage aiMsg) {
                String text = aiMsg.text();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * 格式化消息用于返回给前端
     *
     * @param msg 消息对象
     * @return 格式化后的消息Map
     */
    private Map<String, Object> formatMessage(ChatMessage msg) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("type", msg.getClass().getSimpleName());
        List<ChatArtifact> artifacts = ArtifactResponseExtractor.extractMessageArtifacts(msg, objectMapper);
        if (!artifacts.isEmpty()) {
            formatted.put("artifacts", artifacts);
            String mindmapData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "mindmap", objectMapper);
            if (mindmapData != null && !mindmapData.isBlank()) {
                formatted.put("mindmapData", mindmapData);
            }
            String questionnaireData = ArtifactResponseExtractor.extractLatestArtifactData(artifacts, "questionnaire", objectMapper);
            if (questionnaireData != null && !questionnaireData.isBlank()) {
                formatted.put("questionnaireData", questionnaireData);
            }
        }

        String content;
        if (msg instanceof AiMessage aiMsg) {
            content = ArtifactResponseExtractor.stripPureArtifactText(aiMsg.text(), artifacts, objectMapper);
            ToolExecutionRequest request = firstQuestionRequest(aiMsg);
            if (request != null) {
                List<QuestionDto> questions = askUserQuestionParser.parse(request.arguments(), request.name());
                if (!questions.isEmpty()) {
                    formatted.put("toolName", request.name());
                    formatted.put("toolCallId", request.id());
                    formatted.put("questions", questions);
                    formatted.put("pendingQuestions", questions);
                }
            }
        } else if (msg instanceof UserMessage userMsg) {
            content = userMsg.singleText();
        } else if (msg instanceof SystemMessage sysMsg) {
            content = sysMsg.text();
        } else if (msg instanceof ToolExecutionResultMessage toolMsg) {
            content = ArtifactResponseExtractor.stripPureArtifactText(toolMsg.text(), artifacts, objectMapper);
            formatted.put("toolName", toolMsg.toolName());
            formatted.put("toolCallId", toolMsg.id());
        } else {
            content = null;
        }
        formatted.put("content", content);
        return formatted;
    }

    /** 把持久化历史消息重新整理成前端可直接渲染的结构。 */
    private Map<String, Object> formatHistoryMessage(HistoryMessage historyMessage) {
        if (historyMessage.uiOnly()) {
            Map<String, Object> formatted = new HashMap<>();
            formatted.put("type", "AiMessage");
            formatted.put("content", "");
            if (historyMessage.createdAt() != null) {
                formatted.put("timestamp", historyMessage.createdAt());
            }
            if (historyMessage.timelineActions() != null && !historyMessage.timelineActions().isEmpty()) {
                formatted.put("id", historyActionMessageId(historyMessage.timelineActions()));
                formatted.put("actions", historyMessage.timelineActions());
                Map<String, Object> userQuestionAction = firstUserQuestionAction(historyMessage.timelineActions());
                if (userQuestionAction != null) {
                    formatted.put("pendingId", userQuestionAction.get("pendingId"));
                    formatted.put("toolCallId", userQuestionAction.get("toolCallId"));
                    formatted.put("questions", userQuestionAction.get("questions"));
                    formatted.put("pendingQuestions", userQuestionAction.get("questions"));
                }
            }
            return formatted;
        }

        Map<String, Object> formatted = formatMessage(historyMessage.message());
        if (historyMessage.record() != null && historyMessage.record().getId() != null) {
            formatted.put("id", "history-message-" + historyMessage.record().getId());
        }
        if (historyMessage.record() != null && historyMessage.record().getCreatedAt() != null) {
            formatted.put("timestamp", historyMessage.record().getCreatedAt());
        }
        if (historyMessage.timelineActions() != null && !historyMessage.timelineActions().isEmpty()) {
            formatted.put("actions", historyMessage.timelineActions());
        }
        return formatted;
    }

    /** 给仅包含 timeline action 的历史消息生成一个稳定展示 ID。 */
    private String historyActionMessageId(List<Map<String, Object>> timelineActions) {
        if (timelineActions != null && !timelineActions.isEmpty()) {
            Object firstId = timelineActions.getFirst().get("id");
            if (firstId != null && !String.valueOf(firstId).isBlank()) {
                return "history-action-message-" + firstId;
            }
        }
        return "history-action-message-" + UUID.randomUUID();
    }

    /** 从时间线动作里找出第一条用户补充信息类 action。 */
    private Map<String, Object> firstUserQuestionAction(List<Map<String, Object>> timelineActions) {
        if (timelineActions == null) {
            return null;
        }
        for (Map<String, Object> action : timelineActions) {
            if ("user_question".equals(String.valueOf(action.get("kind")))) {
                return action;
            }
        }
        return null;
    }

    /** 从 AI 消息的工具调用里找出第一条提问类工具请求。 */
    private ToolExecutionRequest firstQuestionRequest(AiMessage aiMsg) {
        if (aiMsg == null || !aiMsg.hasToolExecutionRequests()) {
            return null;
        }

        for (ToolExecutionRequest request : aiMsg.toolExecutionRequests()) {
            if (isQuestionTool(request.name())) {
                return request;
            }
        }
        return null;
    }

    /** 判断一个工具名是不是 AskUserQuestion 这类会挂起等待用户回答的工具。 */
    private boolean isQuestionTool(String toolName) {
        return "askUserQuestion".equals(toolName)
                || "askMultipleQuestions".equals(toolName)
                || "askQuestionnaire".equals(toolName);
    }

    /** 统计本轮总 token 用量，并把子 Agent 的消耗一并折算进去。 */
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

    /** 构建最简版 message_done 事件载荷，给无 artifact 的普通回复使用。 */
    private Map<String, Object> buildMessageDonePayload(String content, String mindmapData, String questionnaireData) {
        return buildMessageDonePayload(content, mindmapData, questionnaireData, List.of());
    }

    /** 构建完整的 message_done 事件载荷，把可见回复内容一次性发给前端。 */
    private Map<String, Object> buildMessageDonePayload(
            String content,
            String mindmapData,
            String questionnaireData,
            List<ChatArtifact> artifacts) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("role", "assistant");
        payload.put("content", content != null ? content : "");
        payload.put("streaming", true);
        payload.put("artifacts", artifacts != null ? artifacts : List.of());
        if (mindmapData != null && !mindmapData.isBlank()) {
            payload.put("mindmapData", mindmapData);
        }
        if (questionnaireData != null && !questionnaireData.isBlank()) {
            payload.put("questionnaireData", questionnaireData);
        }
        return payload;
    }

    /** 判断一条助手结果是否有任何值得前端展示的可见内容。 */
    private boolean hasVisibleAssistantResult(String content, String mindmapData, String questionnaireData) {
        return hasVisibleAssistantResult(content, mindmapData, questionnaireData, List.of());
    }

    /** 综合文本、图谱、问卷和 artifact 判断这轮助手结果是不是“空回包”。 */
    private boolean hasVisibleAssistantResult(
            String content,
            String mindmapData,
            String questionnaireData,
            List<ChatArtifact> artifacts) {
        return (content != null && !content.isBlank())
                || (mindmapData != null && !mindmapData.isBlank())
                || (questionnaireData != null && !questionnaireData.isBlank())
                || (artifacts != null && !artifacts.isEmpty());
    }

    /** 构建 SSE done 事件载荷，告诉前端本轮会话已经正常收尾。 */
    private Map<String, Object> buildDonePayload(SessionState finalSessionState, QueryLoopState finalInnerState) {
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> taskPlan = finalInnerState.getTaskPlan();
        payload.put("status", "success");
        payload.put("tokenUsage", buildTokenUsagePayload(finalSessionState, finalInnerState));
        payload.put("taskPlan", taskPlan != null ? taskPlan : List.of());
        payload.put("taskProgress", buildTaskProgress(taskPlan));
        return payload;
    }

    /** 统计任务计划进度，方便前端直接展示当前推进情况。 */
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

    /** 只有任务计划真的变化时才推送 task_update，避免前端反复重绘。 */
    private List<Map<String, Object>> sendTaskUpdateIfChanged(
            ChatStreamEventSink sink,
            QueryLoopState innerState,
            List<Map<String, Object>> lastSentTaskPlan) {
        List<Map<String, Object>> currentTaskPlan = innerState.getTaskPlan();
        if (currentTaskPlan == null || currentTaskPlan.equals(lastSentTaskPlan)) {
            return lastSentTaskPlan;
        }

        try {
            sink.send("task_update", Map.of(
                    "taskPlan", currentTaskPlan,
                    "taskProgress", buildTaskProgress(currentTaskPlan)
            ));
        } catch (Exception e) {
            log.warn("[ClaudeController] task_update 发送失败，忽略并继续执行: error={}", e.getMessage());
        }
        return new ArrayList<>(currentTaskPlan);
    }

    /** 以“尽力而为”的方式发送 SSE 事件，失败也不影响主流程收尾。 */
    private void sendBestEffort(String sessionId, ChatStreamEventSink sink, ThrowingConsumer<ChatStreamEventSink> sender) {
        if (sink.isClosed()) {
            return;
        }
        try {
            sender.accept(sink);
        } catch (Exception e) {
            log.warn("[ClaudeController] SSE 事件发送失败，忽略并继续收尾: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    @FunctionalInterface
    /** 允许发送 SSE 时抛出受检异常的简单函数接口。 */
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    /**
     * 注入文件内容到用户消息
     *
     * <p>根据 fileId 获取解析后的文件内容，将其格式化后追加到用户消息中。
     * 这样 LLM 可以直接看到文件内容，不需要调用工具解析。
     */
    private String injectFileContent(String fileId, String userMessage) {
        return fileStorageService.get(fileId)
                .map(parsedFile -> {
                    if (!parsedFile.isSuccess()) {
                        log.warn("[ClaudeController] 文件解析失败: fileId={}, error={}",
                                fileId, parsedFile.getErrorMessage());
                        return userMessage + "\n\n[文件处理失败：" + parsedFile.getErrorMessage() + "]";
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append(userMessage);
                    sb.append("\n\n---\n");
                    sb.append("📎 文件内容（").append(parsedFile.getFileName()).append("）：\n");
                    sb.append("---\n");
                    sb.append(parsedFile.getContent());
                    sb.append("\n---");

                    log.info("[ClaudeController] 注入文件内容: fileId={}, fileName={}, contentLength={}",
                            fileId, parsedFile.getFileName(), parsedFile.getContent().length());

                    return sb.toString();
                })
                .orElseGet(() -> {
                    log.warn("[ClaudeController] 文件不存在或已过期: fileId={}", fileId);
                    return userMessage + "\n\n[文件不存在或已过期，请重新上传]";
                });
    }

    /** 根据当前登录用户和租户身份组装出系统提示词需要的用户画像。 */
    private UserProfile buildUserProfile(Account account,
                                         OpenVikingIdentity identity,
                                         String language,
                                         String outputStyle) {
        Map<String, Object> businessContext = new HashMap<>();
        if (account.getId() != null) {
            businessContext.put("jarvisUserId", account.getId());
        }
        businessContext.put("openVikingAccount", identity.account());
        businessContext.put("openVikingUser", identity.user());
        businessContext.put("openVikingAgent", identity.agent());

        return UserProfile.builder()
                .userId(account.getUsername())
                .username(account.getUsername())
                .language(language)
                .outputStyle(outputStyle)
                .businessContext(Collections.unmodifiableMap(businessContext))
                .build();
    }

}
