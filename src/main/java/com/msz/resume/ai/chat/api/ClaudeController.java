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
import com.msz.resume.ai.chat.session.converter.AttachmentMetadata;
import com.msz.resume.ai.chat.session.converter.ChatMessageTextExtractor;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamEventSink;
import com.msz.resume.ai.chat.runtime.trace.ChatRunTraceContext;
import com.msz.resume.ai.chat.runtime.trace.ChatStreamContext;
import com.msz.resume.ai.chat.runtime.trace.InMemoryTimelineActionRecorder;
import com.msz.resume.ai.chat.runtime.trace.TimelineActionRecorder;
import com.msz.resume.ai.chat.runtime.trace.stream.TraceReplayService;
import com.msz.resume.ai.chat.runtime.trace.stream.TimelineActionRecorderFactory;
import com.msz.resume.ai.chat.runtime.trace.TraceAgentDescriptor;
import com.msz.resume.ai.chat.runtime.trace.TraceService;
import com.msz.resume.ai.chat.runtime.trace.langfuse.LangfuseTracingService;
import com.msz.resume.ai.file.dto.ParsedFile;
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
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int MAX_IMAGE_ATTACHMENTS = 10;
    private static final long MAX_TOTAL_IMAGE_ATTACHMENT_BYTES = 25L * 1024L * 1024L;
    private static final String THINKING_MODE_REASONING_EFFORT = "high";
    private static final String NON_THINKING_MODE_REASONING_EFFORT = "none";
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
    private final LangfuseTracingService langfuseTracingService;
    private final TimelineActionRecorderFactory timelineActionRecorderFactory;
    private final TraceReplayService traceReplayService;
    private final long chatStreamTimeoutMs;

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
                            LangfuseTracingService langfuseTracingService,
                            TimelineActionRecorderFactory timelineActionRecorderFactory,
                            TraceReplayService traceReplayService,
                            @Value("${jarvis.chat.stream-timeout-ms:600000}") long chatStreamTimeoutMs) {
        this.queryEngineGraph = queryEngineGraph;
        this.persistenceService = persistenceService;
        this.openVikingSessionGateway = openVikingSessionGateway;
        this.currentAccountResolver = currentAccountResolver;
        this.openVikingIdentityResolver = openVikingIdentityResolver;
        this.objectMapper = objectMapper;
        this.askUserQuestionParser = askUserQuestionParser;
        this.fileStorageService = fileStorageService;
        this.traceService = traceService;
        this.langfuseTracingService = langfuseTracingService;
        this.timelineActionRecorderFactory = timelineActionRecorderFactory;
        this.traceReplayService = traceReplayService;
        this.chatStreamTimeoutMs = chatStreamTimeoutMs;
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
            String reasoningEffort = resolveReasoningEffort(request);
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
                innerInput.put(QueryLoopState.LLM_CONTEXT_CHECKPOINT, snapshot.state().getInnerState().getLlmContextCheckpoint());
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

            UserMessage userMessage = buildUserMessage(request);
            messages.add(userMessage);

            // ========== 3.1 追加用户消息到 OpenViking Session（最佳努力） ==========
            try {
                openVikingSessionGateway.appendUserMessage(sessionId, ChatMessageTextExtractor.userText(userMessage), identity);
            } catch (Exception e) {
                log.warn("[OpenViking] appendUserMessage 异常: sessionId={}, error={}", sessionId, e.getMessage());
            }

            // 创建带有用户消息的新内层状态
            innerInput.put(QueryLoopState.MESSAGE_HISTORY, messages);
            innerInput.put(QueryLoopState.TASK_PLAN, innerState.getTaskPlan());
            innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, innerState.getSurfacedOpenVikingUris());
            innerInput.put(QueryLoopState.LLM_CONTEXT_CHECKPOINT, innerState.getLlmContextCheckpoint());
            innerInput.put(QueryLoopState.REASONING_EFFORT, reasoningEffort);
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
            List<ChatMessage> finalMessages = finalInnerState.getMessages();
            List<ChatArtifact> artifacts = ArtifactResponseExtractor.extractLatestArtifacts(finalMessages, objectMapper);
            String aiMessageContent = ArtifactResponseExtractor.extractVisibleAssistantText(
                    finalMessages, artifacts, objectMapper);
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
                                 @RequestParam(required = false) Boolean thinkingMode,
                                 @RequestParam(required = false) String reasoningEffort,
                                 @RequestParam(required = false) String fileId,
                                 HttpServletRequest httpServletRequest) {
        ChatRequest request = new ChatRequest();
        request.setSessionId(sessionId);
        request.setUserMessage(userMessage);
        request.setLanguage(language);
        request.setOutputStyle(outputStyle);
        request.setThinkingMode(thinkingMode);
        request.setReasoningEffort(reasoningEffort);
        request.setFileId(fileId);
        return chatStreamInternal(request, httpServletRequest);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamPost(@RequestBody ChatRequest request,
                                     HttpServletRequest httpServletRequest) {
        return chatStreamInternal(request, httpServletRequest);
    }

    private SseEmitter chatStreamInternal(ChatRequest request,
                                          HttpServletRequest httpServletRequest) {
        if (request == null) {
            request = new ChatRequest();
        }
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            request.setSessionId(sessionId);
        }
        String userMessage = request.getUserMessage() != null ? request.getUserMessage() : "";
        String language = request.getLanguage();
        String outputStyle = request.getOutputStyle();
        String reasoningEffort = resolveReasoningEffort(request);
        Account currentAccount = currentAccountResolver.requireCurrentAccount(httpServletRequest, "chat/stream");
        OpenVikingIdentity identity = openVikingIdentityResolver.resolve(currentAccount);
        UserProfile userContext = buildUserProfile(currentAccount, identity, language, outputStyle);

        SseEmitter emitter = new SseEmitter(chatStreamTimeoutMs);
        InMemoryTimelineActionRecorder timelineActionRecorder = new InMemoryTimelineActionRecorder();
        TimelineActionRecorder recorder = timelineActionRecorderFactory.withTraceStream(sessionId, timelineActionRecorder);
        ChatStreamEventSink sink = new ChatStreamEventSink(emitter, objectMapper, sessionId, recorder);
        String runId = UUID.randomUUID().toString();
        ChatRunTraceContext traceContext = new ChatRunTraceContext(runId, sessionId, sink);
        langfuseTracingService.startTrace(traceContext, currentAccount.getUsername(), userMessage);
        ChatRequest streamRequest = request;
        String effectiveSessionId = sessionId;

        CompletableFuture.runAsync(() -> {
            SessionState finalSessionState = null;
            try {
                OpenVikingIdentityContextHolder.set(identity);
                ChatStreamContext.bindRun(effectiveSessionId, traceContext);
                sendBestEffort(effectiveSessionId, sink, ignored ->
                        sink.send("session_started", Map.of(
                                "status", "started",
                                "streaming", true,
                                "runId", runId
                        )));
                traceService.startLlmRound(traceContext, TraceAgentDescriptor.mainAgent());

                // 从数据库恢复会话，构造输入数据
                SessionSnapshot snapshot = persistenceService.restoreSession(effectiveSessionId, currentAccount.getUsername());
                QueryLoopState innerState;
                if (snapshot != null) {
                    Map<String, Object> innerInput = new HashMap<>();
                    innerInput.put(QueryLoopState.MESSAGE_HISTORY, snapshot.messages());
                    innerInput.put(QueryLoopState.TASK_PLAN, snapshot.state().getInnerState().getTaskPlan());
                    innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, snapshot.state().getInnerState().getSurfacedOpenVikingUris());
                    innerInput.put(QueryLoopState.LLM_CONTEXT_CHECKPOINT, snapshot.state().getInnerState().getLlmContextCheckpoint());
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

                UserMessage finalUserMessage = buildUserMessage(streamRequest);
                String finalUserMessageText = ChatMessageTextExtractor.userText(finalUserMessage);
                langfuseTracingService.updateTraceInput(traceContext, finalUserMessageText);

                Map<String, Object> innerInput = new HashMap<>();
                List<ChatMessage> messages = new ArrayList<>(innerState.getMessages());
                messages.add(finalUserMessage);
                innerInput.put(QueryLoopState.MESSAGE_HISTORY, messages);
                innerInput.put(QueryLoopState.TASK_PLAN, innerState.getTaskPlan());
                innerInput.put(QueryLoopState.SURFACED_OPENVIKING_URIS, innerState.getSurfacedOpenVikingUris());
                innerInput.put(QueryLoopState.LLM_CONTEXT_CHECKPOINT, innerState.getLlmContextCheckpoint());
                innerInput.put(QueryLoopState.REASONING_EFFORT, reasoningEffort);
                innerInput.put(QueryLoopState.OPENVIKING_IDENTITY, identity);
                innerInput.put(QueryLoopState.TRACE_RUN_ID, runId);
                innerInput.put(QueryLoopState.TRACE_AGENT_ID, innerState.getTraceAgentId());
                innerInput.put(QueryLoopState.TRACE_AGENT_LABEL, innerState.getTraceAgentLabel());
                innerInput.put(QueryLoopState.TRACE_AGENT_SCOPE, innerState.getTraceAgentScope());
                QueryLoopState newInnerState = new QueryLoopState(innerInput);

                // ========== 追加用户消息到 OpenViking Session（最佳努力） ==========
                try {
                    openVikingSessionGateway.appendUserMessage(effectiveSessionId, finalUserMessageText, identity);
                } catch (Exception e) {
                    log.warn("[OpenViking] appendUserMessage 异常 (SSE): sessionId={}, error={}", effectiveSessionId, e.getMessage());
                }

                Map<String, Object> sessionInput = new HashMap<>();
                sessionInput.put(SessionState.SESSION_ID, effectiveSessionId);
                sessionInput.put(SessionState.INNER_STATE, newInnerState);
                sessionInput.put(SessionState.USER_CONTEXT, userContext);
                sessionInput.put(SessionState.OPENVIKING_IDENTITY, identity);
                RunnableConfig runConfig = RunnableConfig.builder().threadId(effectiveSessionId).build();

                List<Map<String, Object>> lastSentTaskPlan = Collections.emptyList();
                for (NodeOutput<SessionState> output : queryEngineGraph.stream(sessionInput, runConfig)) {
                    log.info("[SSE执行步骤] 节点: {}, 会话ID: {}", output.node(), effectiveSessionId);
                    finalSessionState = output.state();
                    if (!sink.isClosed() && finalSessionState != null && finalSessionState.getInnerState() != null) {
                        lastSentTaskPlan = sendTaskUpdateIfChanged(sink, finalSessionState.getInnerState(), lastSentTaskPlan);
                    }
                }

                if (finalSessionState == null || finalSessionState.getInnerState() == null) {
                    traceService.failMainLlmRound(traceContext);
                    langfuseTracingService.failTrace(traceContext, "状态机执行失败：返回空结果");
                    ChatStreamContext.clear(effectiveSessionId);
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
                    langfuseTracingService.failTrace(traceContext, finalInnerState.getErrorMessage());
                    String errorMessage = finalInnerState.getErrorMessage();
                    sendBestEffort(effectiveSessionId, sink, ignored ->
                            sink.error(errorType, errorMessage));
                    ChatStreamContext.clear(effectiveSessionId);
                    if (!sink.isClosed()) {
                        sink.complete();
                    }
                    return;
                }

                List<ChatMessage> finalMessages = finalInnerState.getMessages();
                List<ChatArtifact> artifacts = ArtifactResponseExtractor.extractLatestArtifacts(finalMessages, objectMapper);
                String lastAiContent = ArtifactResponseExtractor.extractVisibleAssistantText(
                        finalMessages, artifacts, objectMapper);
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
                    sendBestEffort(effectiveSessionId, sink, ignored ->
                            sink.send("message_done", messageDonePayload));

                    // ========== 追加助手消息到 OpenViking Session（最佳努力） ==========
                    if (lastAiContent != null && !lastAiContent.isBlank()) {
                        try {
                            openVikingSessionGateway.appendAssistantMessage(effectiveSessionId, lastAiContent, identity);
                        } catch (Exception e) {
                            log.warn("[OpenViking] appendAssistantMessage 异常 (SSE): sessionId={}, error={}", effectiveSessionId, e.getMessage());
                        }
                    }
                }

                persistenceService.completeRound(
                        effectiveSessionId,
                        currentAccount.getUsername(),
                        finalSessionState,
                        finalInnerState.getMessages(),
                        timelineActionRecorder.snapshot()
                );

                Map<String, Object> donePayload = buildDonePayload(finalSessionState, finalInnerState);
                traceService.completeMainLlmRound(traceContext);
                langfuseTracingService.completeTrace(traceContext,
                        buildTraceOutput(lastAiContent, mindmapData, questionnaireData, artifacts));
                sendBestEffort(effectiveSessionId, sink, ignored ->
                        sink.send("done", donePayload));
                ChatStreamContext.clear(effectiveSessionId);
                if (!sink.isClosed()) {
                    sink.complete();
                }

            } catch (Exception e) {
                log.error("[SSE执行异常] sessionId={}, error={}", effectiveSessionId, e.getMessage(), e);
                traceService.failMainLlmRound(traceContext);
                langfuseTracingService.failTrace(traceContext, e.getMessage());
                ChatStreamContext.clear(effectiveSessionId);
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
            content = ChatMessageTextExtractor.userText(userMsg);
            List<Map<String, Object>> attachments = userAttachments(userMsg);
            if (!attachments.isEmpty()) {
                formatted.put("attachments", attachments);
            }
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
        if (historyMessage.record() != null
                && historyMessage.record().getAttachmentsJson() != null
                && !historyMessage.record().getAttachmentsJson().isBlank()) {
            try {
                formatted.put("attachments", objectMapper.readValue(historyMessage.record().getAttachmentsJson(), List.class));
            } catch (Exception e) {
                log.warn("[ClaudeController] 历史附件解析失败: messageId={}, error={}",
                        historyMessage.record().getId(), e.getMessage());
            }
        }
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

    /** 用前端可见结果生成 Langfuse root trace output，避免纯 artifact 回复被记为空输出。 */
    private String buildTraceOutput(
            String content,
            String mindmapData,
            String questionnaireData,
            List<ChatArtifact> artifacts) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("content", content != null ? content : "");
        output.put("artifactTypes", artifacts != null
                ? artifacts.stream().map(ChatArtifact::getType).toList()
                : List.of());
        if (mindmapData != null && !mindmapData.isBlank()) {
            output.put("mindmapData", mindmapData);
        }
        if (questionnaireData != null && !questionnaireData.isBlank()) {
            output.put("questionnaireData", questionnaireData);
        }
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return content != null ? content : "";
        }
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
                    if ("image".equals(parsedFile.getFileKind())) {
                        return userMessage + "\n\n[图片附件会作为视觉输入发送：" + parsedFile.getFileName() + "]";
                    }
                    if (!parsedFile.isSuccess()) {
                        log.warn("[ClaudeController] 文件解析失败: fileId={}, error={}",
                                fileId, parsedFile.getErrorMessage());
                        return userMessage + "\n\n[文件处理失败：" + parsedFile.getErrorMessage() + "]";
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append(userMessage);
                    sb.append("\n\n---\n");
                    sb.append("已解析上传文件：\n");
                    sb.append("- fileId: ").append(parsedFile.getFileId()).append("\n");
                    sb.append("- fileName: ").append(blankTo(parsedFile.getFileName(), "(未命名文件)")).append("\n");
                    sb.append("- fileType: ").append(blankTo(parsedFile.getFileType(), "(未知类型)")).append("\n");
                    sb.append("- fileKind: ").append(blankTo(parsedFile.getFileKind(), "document")).append("\n");
                    sb.append("如果这个文件是原始简历，调用 evaluateResume 时必须把 fileId 作为 sourceFileId 传入，不要把下方全文复制到 originalResumeText。\n\n");
                    sb.append("文件内容（").append(blankTo(parsedFile.getFileName(), parsedFile.getFileId())).append("）：\n");
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

    private String blankTo(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    UserMessage buildUserMessage(ChatRequest request) {
        String text = request.getUserMessage() != null ? request.getUserMessage() : "";
        String promptText = text;
        String fileId = request.getFileId();
        if (fileId != null && !fileId.isBlank()) {
            promptText = injectFileContent(fileId, promptText);
        }

        List<String> requestedImageIds = imageAttachmentIds(request);
        List<String> imageIds = requestedImageIds.size() > MAX_IMAGE_ATTACHMENTS
                ? requestedImageIds.subList(0, MAX_IMAGE_ATTACHMENTS)
                : requestedImageIds;
        List<Content> contents = new ArrayList<>();
        List<AttachmentMetadata> attachments = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder(promptText);
        if (requestedImageIds.size() > MAX_IMAGE_ATTACHMENTS) {
            textBuilder.append("\n\n[图片附件超过最多 ")
                    .append(MAX_IMAGE_ATTACHMENTS)
                    .append(" 张限制，已忽略后续图片]");
        }

        long totalImageBytes = 0;
        for (String imageId : imageIds) {
            Optional<ParsedFile> parsedFile = fileStorageService.get(imageId);
            if (parsedFile.isEmpty()) {
                textBuilder.append("\n\n[图片已过期或不存在：").append(imageId).append("]");
                attachments.add(AttachmentMetadata.builder()
                        .fileId(imageId)
                        .fileKind("image")
                        .available(false)
                        .build());
                continue;
            }
            ParsedFile image = parsedFile.get();
            if (!image.isSuccess() || !"image".equals(image.getFileKind()) || image.getBase64Data() == null) {
                textBuilder.append("\n\n[图片处理失败：")
                        .append(image.getFileName() != null ? image.getFileName() : imageId)
                        .append("]");
                attachments.add(toAttachmentMetadata(image, false));
                continue;
            }
            if (totalImageBytes + image.getFileSize() > MAX_TOTAL_IMAGE_ATTACHMENT_BYTES) {
                textBuilder.append("\n\n[图片附件总大小超过 25MB 限制，已忽略：")
                        .append(image.getFileName() != null ? image.getFileName() : imageId)
                        .append("]");
                attachments.add(toAttachmentMetadata(image, false));
                continue;
            }
            contents.add(ImageContent.from(image.getBase64Data(), image.getMimeType()));
            attachments.add(toAttachmentMetadata(image, true));
            totalImageBytes += image.getFileSize();
        }

        List<Content> orderedContents = new ArrayList<>();
        orderedContents.add(TextContent.from(textBuilder.toString()));
        orderedContents.addAll(contents);

        UserMessage.Builder builder = UserMessage.builder().contents(orderedContents);
        if (!attachments.isEmpty()) {
            builder.attributes(Map.of("attachments", attachments));
        }
        return builder.build();
    }

    private List<String> imageAttachmentIds(ChatRequest request) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (request.getImageFileIds() != null) {
            ids.addAll(request.getImageFileIds());
        }
        if (request.getAttachmentIds() != null) {
            ids.addAll(request.getAttachmentIds());
        }
        ids.removeIf(id -> id == null || id.isBlank() || id.equals(request.getFileId()));
        return new ArrayList<>(ids);
    }

    private AttachmentMetadata toAttachmentMetadata(ParsedFile parsedFile, boolean available) {
        if (parsedFile == null) {
            return AttachmentMetadata.builder().fileKind("image").available(false).build();
        }
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

    private String resolveReasoningEffort(ChatRequest request) {
        if (request == null) {
            return null;
        }
        String explicitEffort = request.getReasoningEffort();
        if (explicitEffort != null && !explicitEffort.isBlank()) {
            return explicitEffort.trim();
        }
        if (request.getThinkingMode() == null) {
            return null;
        }
        return request.getThinkingMode()
                ? THINKING_MODE_REASONING_EFFORT
                : NON_THINKING_MODE_REASONING_EFFORT;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> userAttachments(UserMessage userMessage) {
        if (userMessage == null || userMessage.attributes() == null) {
            return List.of();
        }
        Object rawAttachments = userMessage.attributes().get("attachments");
        if (!(rawAttachments instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof AttachmentMetadata metadata) {
                result.add(objectMapper.convertValue(metadata, Map.class));
            } else if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(normalized);
            }
        }
        return result;
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
