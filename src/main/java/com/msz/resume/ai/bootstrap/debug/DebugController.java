package com.msz.resume.ai.bootstrap.debug;

import com.msz.resume.ai.chat.compression.*;
import com.msz.resume.ai.chat.compression.model.AutocompactResult;
import com.msz.resume.ai.chat.compression.model.PipelineResult;
import com.msz.resume.ai.chat.compression.model.SplitResult;
import com.msz.resume.ai.chat.api.dto.ChatStreamEvent;
import com.msz.resume.ai.chat.runtime.trace.stream.TraceStreamDebugService;
import com.msz.resume.ai.chat.runtime.trace.stream.TraceReplayService;
import com.msz.resume.ai.hook.HookConfigProperties;
import com.msz.resume.ai.hook.HookRule;
import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.session.OpenVikingSessionGateway;
import com.msz.resume.ai.integrations.openviking.core.session.OpenVikingSessionProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAppendSessionMessageRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCreateSessionRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteResponse;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingMemoryService;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import com.msz.resume.ai.chat.prompt.builder.SystemPromptBuilder;
import com.msz.resume.ai.chat.prompt.model.PromptResult;
import com.msz.resume.ai.chat.prompt.model.UserProfile;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 调试API控制器
 * 提供系统提示词查看、压缩管线测试等调试功能
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final SystemPromptBuilder promptBuilder;
    private final TokenEstimator tokenEstimator;
    private final AutocompactConfig autocompactConfig;
    private final MessageSplitCalculator splitCalculator;
    private final MessagePreprocessingPipeline pipeline;
    private final Autocompact autocompact;
    private final HookConfigProperties hookConfigProperties;
    private final OpenVikingMemoryService openVikingMemoryService;
    private final OpenVikingSkillService openVikingSkillService;
    private final OpenVikingClient openVikingClient;
    private final OpenVikingSessionGateway openVikingSessionGateway;
    private final OpenVikingSessionProperties openVikingSessionProperties;
    private final TraceStreamDebugService traceStreamDebugService;
    private final TraceReplayService traceReplayService;

    /**
     * 获取完整的系统提示词
     * 用于调试和验证系统提示词构建是否正确
     *
     * @param userId   用户ID（可选）
     * @param username 用户名（可选）
     * @return 包含完整系统提示词、token估算值、静态部分、动态部分
     */
    @GetMapping("/system-prompt")
    public Map<String, Object> getSystemPrompt(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String outputStyle) {

        log.info("[DebugController] 获取系统提示词, userId={}, username={}, language={}, outputStyle={}",
                userId, username, language, outputStyle);

        UserProfile userContext = UserProfile.builder()
                .userId(userId != null ? userId : "anonymous")
                .username(username)
                .language(language)
                .outputStyle(outputStyle)
                .build();

        PromptResult result = promptBuilder.build(userContext);

        return Map.of(
                "systemPrompt", result.systemPrompt(),
                "tokenEstimate", result.tokenEstimate(),
                "staticSectionContent", result.staticSectionContent(),
                "dynamicSectionContent", result.dynamicSectionContent()
        );
    }

    /**
     * 获取单个section的内容
     *
     * @param sectionName section名称（如 tool_usage_guide, user_context 等）
     * @return section内容
     */
    @GetMapping("/system-prompt/section")
    public Map<String, Object> getSection(@RequestParam String sectionName) {
        log.info("[DebugController] 获取section: {}", sectionName);

        String content = promptBuilder.getSection(sectionName);
        boolean enabled = promptBuilder.isSectionEnabled(sectionName);

        return Map.of(
                "sectionName", sectionName,
                "content", content != null ? content : "(not found)",
                "enabled", enabled
        );
    }

    /**
     * 热更新系统提示词配置
     * 重新从YAML文件加载配置
     *
     * @return 操作结果
     */
    @GetMapping("/system-prompt/reload")
    public Map<String, Object> reloadSystemPrompt() {
        log.info("[DebugController] 热更新系统提示词配置");

        try {
            promptBuilder.reload();
            return Map.of(
                    "success", true,
                    "message", "系统提示词配置已重新加载"
            );
        } catch (Exception e) {
            log.error("[DebugController] 热更新失败", e);
            return Map.of(
                    "success", false,
                    "message", "热更新失败: " + e.getMessage()
            );
        }
    }

    // ==================== Hook 调试接口 ====================

    /**
     * 获取当前 Hook 配置
     *
     * @return 当前 PreToolUse 和 PostToolUse 规则列表
     */
    @GetMapping("/hooks/config")
    public Map<String, Object> getHookConfig() {
        log.info("[DebugController] 获取Hook配置");

        List<Map<String, Object>> preRules = hookConfigProperties.getPreToolUseRules().stream()
                .map(rule -> Map.<String, Object>of(
                        "name", rule.name(),
                        "matcher", rule.matcher(),
                        "action", rule.action(),
                        "priority", rule.priority()))
                .toList();

        List<Map<String, Object>> postRules = hookConfigProperties.getPostToolUseRules().stream()
                .map(rule -> Map.<String, Object>of(
                        "name", rule.name(),
                        "matcher", rule.matcher(),
                        "action", rule.action(),
                        "priority", rule.priority()))
                .toList();

        return Map.of(
                "preToolUse", preRules,
                "postToolUse", postRules
        );
    }

    /**
     * 热更新 Hook 配置
     * 重新从YAML文件加载配置
     *
     * @return 操作结果
     */
    @GetMapping("/hooks/reload")
    public Map<String, Object> reloadHookConfig() {
        log.info("[DebugController] 热更新Hook配置");

        try {
            hookConfigProperties.reload();
            return Map.of(
                    "success", true,
                    "message", "Hook配置已重新加载"
            );
        } catch (Exception e) {
            log.error("[DebugController] Hook配置热更新失败", e);
            return Map.of(
                    "success", false,
                    "message", "Hook配置热更新失败: " + e.getMessage()
            );
        }
    }

    @GetMapping("/trace/stream/status")
    public Map<String, Object> getTraceStreamStatus() {
        return traceStreamDebugService.getStatus();
    }

    @GetMapping("/trace/stream/recent")
    public List<Map<String, Object>> getRecentTraceStreamEvents(
            @RequestParam(defaultValue = "20") int count) {
        return traceStreamDebugService.recentEvents(count);
    }

    @GetMapping("/trace/stream/dead-letter/recent")
    public List<Map<String, Object>> getRecentTraceDeadLetters(
            @RequestParam(defaultValue = "20") int count) {
        return traceStreamDebugService.recentDeadLetters(count);
    }

    @GetMapping("/trace/stream/replay")
    public List<ChatStreamEvent> replayTraceStreamEvents(
            @RequestParam String sessionId,
            @RequestParam(required = false) String runId,
            @RequestParam(defaultValue = "0") long lastSequence,
            @RequestParam(defaultValue = "50") int count) {
        return traceReplayService.replaySince(sessionId, runId, lastSequence, count);
    }


    /**
     * 创建指定 ID 的 OpenViking Session。
     *
     * <p>用途：验证 JARVIS 能否带着当前 OpenViking 配置创建服务端会话。</p>
     */
    @PostMapping("/open-viking/sessions")
    public Map<String, Object> createOpenVikingSession(@RequestBody OpenVikingCreateSessionRequest request) {
        log.info("[DebugController] 创建 OpenViking Session, sessionId={}", request != null ? request.sessionId() : null);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Session 创建成功",
                    "response", openVikingClient.createSession(request != null ? request.sessionId() : null)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Session 创建失败", e);
        }
    }

    /**
     * 向指定 OpenViking Session 追加单条消息。
     */
    @PostMapping("/open-viking/sessions/{sessionId}/messages")
    public Map<String, Object> appendOpenVikingSessionMessage(
            @PathVariable String sessionId,
            @RequestBody OpenVikingAppendSessionMessageRequest request) {
        log.info("[DebugController] 追加 OpenViking Session 消息, sessionId={}, role={}", sessionId, request != null ? request.role() : null);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Session 消息追加成功",
                    "response", openVikingClient.appendSessionMessage(sessionId, request)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Session 消息追加失败", e);
        }
    }

    /**
     * 读取指定 OpenViking Session 的上下文。
     */
    @GetMapping("/open-viking/sessions/{sessionId}/context")
    public Map<String, Object> getOpenVikingSessionContext(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer tokenBudget) {
        log.info("[DebugController] 读取 OpenViking Session Context, sessionId={}, tokenBudget={}", sessionId, tokenBudget);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Session Context 读取成功",
                    "response", openVikingClient.getSessionContext(sessionId, tokenBudget)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Session Context 读取失败", e);
        }
    }

    /**
     * 使用 OpenViking session-aware search 检索。
     */
    @PostMapping("/open-viking/search")
    public Map<String, Object> searchOpenViking(@RequestBody OpenVikingSearchRequest request) {
        log.info("[DebugController] OpenViking session-aware search, query={}, sessionId={}",
                request != null ? request.query() : null,
                request != null ? request.sessionId() : null);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking session-aware search 成功",
                    "response", openVikingClient.search(request)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking session-aware search 失败", e);
        }
    }

    /**
     * 提交指定 OpenViking Session，不轮询后台任务。
     */
    @PostMapping("/open-viking/sessions/{sessionId}/commit")
    public Map<String, Object> commitOpenVikingSession(@PathVariable String sessionId) {
        log.info("[DebugController] 提交 OpenViking Session, sessionId={}", sessionId);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Session 提交成功",
                    "response", openVikingClient.commitSession(sessionId)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Session 提交失败", e);
        }
    }

    /**
     * 手动提交指定 OpenViking Session 到归档/记忆提取流程。
     *
     * <p>这是 Phase 4 的显式调试入口，只在 manualCommit 开启时可用，
     * 不会在普通对话链路中自动触发。</p>
     */
    @PostMapping("/open-viking/sessions/{sessionId}/manual-commit")
    public Map<String, Object> manualCommitOpenVikingSession(@PathVariable String sessionId) {
        log.info("[DebugController] 手动提交 OpenViking Session, sessionId={}", sessionId);

        if (!openVikingSessionProperties.isManualCommit()) {
            return Map.of(
                    "success", false,
                    "message", "OpenViking manual commit 未启用",
                    "sessionId", sessionId
            );
        }

        Optional<String> taskId = openVikingSessionGateway.commitSession(sessionId);
        if (taskId.isPresent()) {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Session 手动提交成功",
                    "sessionId", sessionId,
                    "taskId", taskId.get()
            );
        }

        return Map.of(
                "success", false,
                "message", "OpenViking Session 手动提交失败，请查看服务端日志",
                "sessionId", sessionId
        );
    }

    /**
     * 查询一次 OpenViking 后台任务状态，不轮询。
     */
    @GetMapping("/open-viking/tasks/{taskId}")
    public Map<String, Object> getOpenVikingTask(@PathVariable String taskId) {
        log.info("[DebugController] 查询 OpenViking Task, taskId={}", taskId);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Task 查询成功",
                    "response", openVikingClient.getTask(taskId)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Task 查询失败", e);
        }
    }

    /**
     * 列出 OpenViking Skill 目录。
     */
    @GetMapping("/open-viking/skills")
    public Map<String, Object> listOpenVikingSkills() {
        log.info("[DebugController] 列出 OpenViking Skills");
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 列表读取成功",
                    "response", openVikingSkillService.listSkills()
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 列表读取失败", e);
        }
    }

    /**
     * 添加 OpenViking Skill，支持结构化 Skill 数据或原始 SKILL.md 文本。
     */
    @PostMapping("/open-viking/skills")
    public Map<String, Object> addOpenVikingSkill(@RequestBody Map<String, Object> request) {
        Object data = request != null ? request.get("data") : null;
        log.info("[DebugController] 添加 OpenViking Skill, dataType={}", data != null ? data.getClass().getSimpleName() : null);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 添加成功",
                    "response", openVikingSkillService.addSkill(data)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 添加失败", e);
        }
    }

    /**
     * 上传 SKILL.md 或 zip 后添加 OpenViking Skill。
     */
    @PostMapping(value = "/open-viking/skills/upload", consumes = "multipart/form-data")
    public Map<String, Object> uploadOpenVikingSkill(@RequestPart("file") MultipartFile file) {
        log.info("[DebugController] 上传 OpenViking Skill 文件, filename={}", file != null ? file.getOriginalFilename() : null);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 上传添加成功",
                    "response", openVikingSkillService.uploadSkill(file)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 上传添加失败", e);
        }
    }

    /**
     * 检索 OpenViking Skill 根目录。
     */
    @GetMapping("/open-viking/skills/search")
    public Map<String, Object> searchOpenVikingSkills(
            @RequestParam String query,
            @RequestParam(required = false) Integer limit) {
        log.info("[DebugController] 检索 OpenViking Skills, query={}, limit={}", query, limit);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 检索成功",
                    "response", openVikingSkillService.searchSkills(query, limit)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 检索失败", e);
        }
    }

    /**
     * 读取 OpenViking Skill 主文件 SKILL.md。
     */
    @GetMapping("/open-viking/skills/{name}/read")
    public Map<String, Object> readOpenVikingSkillMain(@PathVariable String name) {
        log.info("[DebugController] 读取 OpenViking Skill 主文件, name={}", name);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 主文件读取成功",
                    "response", openVikingSkillService.readSkillMain(name)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 主文件读取失败", e);
        }
    }

    /**
     * 读取 OpenViking Skill L0 摘要（.abstract.md）。
     */
    @GetMapping("/open-viking/skills/{name}/abstract")
    public Map<String, Object> readOpenVikingSkillAbstract(@PathVariable String name) {
        log.info("[DebugController] 读取 OpenViking Skill 摘要, name={}", name);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 摘要读取成功",
                    "response", openVikingSkillService.readSkillAbstract(name)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 摘要读取失败", e);
        }
    }

    /**
     * 读取 OpenViking Skill L1 概览（.overview.md）。
     */
    @GetMapping("/open-viking/skills/{name}/overview")
    public Map<String, Object> readOpenVikingSkillOverview(@PathVariable String name) {
        log.info("[DebugController] 读取 OpenViking Skill 概览, name={}", name);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 概览读取成功",
                    "response", openVikingSkillService.readSkillOverview(name)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 概览读取失败", e);
        }
    }

    /**
     * 读取 OpenViking Skill 文件树。
     */
    @GetMapping("/open-viking/skills/{name}/files")
    public Map<String, Object> listOpenVikingSkillFiles(@PathVariable String name) {
        log.info("[DebugController] 读取 OpenViking Skill 文件树, name={}", name);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 文件树读取成功",
                    "response", openVikingSkillService.listSkillFiles(name)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 文件树读取失败", e);
        }
    }

    /**
     * 读取 OpenViking Skill 内任意安全相对路径文件。
     */
    @GetMapping("/open-viking/skills/{name}/files/read")
    public Map<String, Object> readOpenVikingSkillFile(
            @PathVariable String name,
            @RequestParam String path) {
        log.info("[DebugController] 读取 OpenViking Skill 文件, name={}, path={}", name, path);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 文件读取成功",
                    "response", openVikingSkillService.readSkillFile(name, path)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 文件读取失败", e);
        }
    }

    /**
     * 递归删除整个 OpenViking Skill。
     */
    @DeleteMapping("/open-viking/skills/{name}")
    public Map<String, Object> deleteOpenVikingSkill(@PathVariable String name) {
        log.info("[DebugController] 删除 OpenViking Skill, name={}", name);
        try {
            return Map.of(
                    "success", true,
                    "message", "OpenViking Skill 删除成功",
                    "response", openVikingSkillService.deleteSkill(name)
            );
        } catch (Exception e) {
            return openVikingError("OpenViking Skill 删除失败", e);
        }
    }

    /**
     * 最小 OpenViking 偏好写入验证接口。
     *
     * <p>用途：
     * 仅用于验证 JARVIS -> OpenVikingMemoryService -> OpenVikingClient -> OpenViking
     * 这条写入链路是否打通。</p>
     *
     * <p>当前接口刻意收敛为“写用户偏好”，不开放任意 URI，避免调试期间把数据结构写乱。</p>
     *
     * @param userId 目标用户 ID
     * @param key 偏好键名，例如 language-style
     * @param content 偏好内容
     * @return 写入结果与实际落盘 URI，便于前端或 OpenViking 控制台观测
     */
    @PostMapping("/openviking/preferences/write")
    public Map<String, Object> writeOpenVikingPreference(
            @RequestParam String userId,
            @RequestParam String key,
            @RequestParam String content) {

        log.info("[DebugController] 写入 OpenViking 偏好, userId={}, key={}", userId, key);

        String sanitizedKey = key.trim()
                .replace(" ", "-")
                .replace("/", "-")
                .replace("\\", "-");
        String uri = "viking://user/" + userId.trim() + "/memories/preferences/" + sanitizedKey + ".md";

        try {
            OpenVikingWriteResponse response = openVikingMemoryService.writePreference(userId, key, content);
            return Map.of(
                    "success", true,
                    "message", "OpenViking 用户偏好写入成功",
                    "uri", uri,
                    "response", response
            );
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            boolean maybePersisted = message.toLowerCase().contains("timed out") || message.toLowerCase().contains("timeout");
            log.warn("[DebugController] OpenViking 偏好写入失败, uri={}, message={}", uri, message);
            return Map.of(
                    "success", false,
                    "message", maybePersisted
                            ? "OpenViking 写入请求超时，但文件可能已经落盘，请到目标 URI 观测。"
                            : "OpenViking 用户偏好写入失败",
                    "uri", uri,
                    "maybePersisted", maybePersisted,
                    "error", message
            );
        }
    }

    /**
     * 最小 OpenViking 偏好读取验证接口。
     *
     * <p>用途：
     * 验证 JARVIS 能否通过 OpenVikingMemoryService 读取之前写入的用户偏好。</p>
     *
     * @param userId 目标用户 ID
     * @param key 偏好键名，例如 language-style
     * @return 读取结果与实际读取 URI，便于和 OpenViking 前端交叉验证
     */
    @GetMapping("/openviking/preferences/read")
    public Map<String, Object> readOpenVikingPreference(
            @RequestParam String userId,
            @RequestParam String key) {

        log.info("[DebugController] 读取 OpenViking 偏好, userId={}, key={}", userId, key);

        String sanitizedKey = key.trim()
                .replace(" ", "-")
                .replace("/", "-")
                .replace("\\", "-");
        String uri = "viking://user/" + userId.trim() + "/memories/preferences/" + sanitizedKey + ".md";

        try {
            OpenVikingReadResponse response = openVikingMemoryService.readPreference(userId, key);
            return Map.of(
                    "success", true,
                    "message", "OpenViking 用户偏好读取成功",
                    "uri", uri,
                    "content", response.result(),
                    "response", response
            );
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("[DebugController] OpenViking 偏好读取失败, uri={}, message={}", uri, message);
            return Map.of(
                    "success", false,
                    "message", "OpenViking 用户偏好读取失败",
                    "uri", uri,
                    "error", message
            );
        }
    }

    /**
     * 获取压缩配置
     *
     * @return 当前压缩配置参数
     */
    @GetMapping("/compression/config")
    public Map<String, Object> getCompressionConfig() {
        log.info("[DebugController] 获取压缩配置");
        return Map.ofEntries(
                // 触发阈值
                Map.entry("triggerThreshold", autocompactConfig.getTriggerThreshold()),
                Map.entry("contextWindow", autocompactConfig.getContextWindow()),
                Map.entry("reservedOutputTokens", autocompactConfig.getReservedOutputTokens()),
                Map.entry("thresholdOffset", autocompactConfig.getThresholdOffset()),

                // 尾部保护
                Map.entry("minTokensToPreserve", autocompactConfig.getMinTokensToPreserve()),
                Map.entry("minMessagesToKeep", autocompactConfig.getMinMessagesToKeep()),
                Map.entry("maxTokensToPreserve", autocompactConfig.getMaxTokensToPreserve()),

                // 压缩后恢复
                Map.entry("skillRestoreBudget", autocompactConfig.getSkillRestoreBudget()),
                Map.entry("maxTokensPerSkill", autocompactConfig.getMaxTokensPerSkill()),
                Map.entry("maxSkillsToRestore", autocompactConfig.getMaxSkillsToRestore()),
                Map.entry("planRestoreBudget", autocompactConfig.getPlanRestoreBudget()),

                // 熔断器
                Map.entry("maxConsecutiveFailures", autocompactConfig.getMaxConsecutiveFailures()),
                Map.entry("maxPtlRetries", autocompactConfig.getMaxPtlRetries())
        );
    }

    /**
     * 测试 Token 估算
     *
     * @param text 要估算的文本
     * @return 估算结果
     */
    @GetMapping("/compression/token-estimate")
    public Map<String, Object> estimateTokens(@RequestParam String text) {
        log.info("[DebugController] Token估算, 文本长度={}", text.length());

        List<ChatMessage> messages = List.of(UserMessage.from(text));
        int tokens = tokenEstimator.estimate(messages);

        return Map.of(
                "textLength", text.length(),
                "estimatedTokens", tokens,
                "ratio", text.length() > 0 ? (double) text.length() / tokens : 0
        );
    }

    /**
     * 测试消息分割计算
     *
     * <p>生成模拟消息列表，测试分割点计算逻辑。
     *
     * @param messageCount   消息数量（默认 20）
     * @param includeTools   是否包含工具调用（默认 true）
     * @param sessionId      会话ID（默认 test-session）
     * @return 分割计算结果
     */
    @GetMapping("/compression/split-test")
    public Map<String, Object> testSplitCalculator(
            @RequestParam(defaultValue = "20") int messageCount,
            @RequestParam(defaultValue = "true") boolean includeTools,
            @RequestParam(defaultValue = "test-session") String sessionId) {

        log.info("[DebugController] 测试分割计算, messageCount={}, includeTools={}", messageCount, includeTools);

        // 生成模拟消息列表
        List<ChatMessage> messages = generateMockMessages(messageCount, includeTools);

        // 计算总 token 数
        int totalTokens = tokenEstimator.estimate(messages);

        // 计算分割点
        SplitResult split = splitCalculator.calculateSplitIndex(messages);

        // 构建结果
        List<Map<String, Object>> messageDetails = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            int msgTokens = tokenEstimator.estimate(List.of(msg));
            messageDetails.add(Map.of(
                    "index", i,
                    "type", msg.getClass().getSimpleName(),
                    "tokens", msgTokens,
                    "isPreserved", i >= split.splitIndex()
            ));
        }

        return Map.of(
                "totalMessages", messageCount,
                "totalTokens", totalTokens,
                "splitIndex", split.splitIndex(),
                "preservedCount", split.preservedCount(),
                "preservedTokens", split.preservedTokens(),
                "shouldSplit", split.shouldSplit(),
                "messages", messageDetails,
                "config", Map.of(
                        "minTokensToPreserve", autocompactConfig.getMinTokensToPreserve(),
                        "minMessagesToKeep", autocompactConfig.getMinMessagesToKeep(),
                        "maxTokensToPreserve", autocompactConfig.getMaxTokensToPreserve()
                )
        );
    }

    /**
     * 测试完整预处理管线
     *
     * <p>生成模拟消息列表，执行完整压缩管线（L1 → L3 → L5）。
     *
     * @param messageCount   消息数量（默认 50）
     * @param includeTools   是否包含工具调用（默认 true）
     * @param sessionId      会话ID（默认 test-session）
     * @return 压缩结果
     */
    @GetMapping("/compression/pipeline-test")
    public Map<String, Object> testPipeline(
            @RequestParam(defaultValue = "50") int messageCount,
            @RequestParam(defaultValue = "true") boolean includeTools,
            @RequestParam(defaultValue = "test-session") String sessionId) {

        log.info("[DebugController] 测试压缩管线, messageCount={}, includeTools={}", messageCount, includeTools);

        // 生成模拟消息列表
        List<ChatMessage> messages = generateMockMessages(messageCount, includeTools);

        // 执行管线
        PipelineResult result = pipeline.process(messages, sessionId);

        return Map.of(
                "originalMessageCount", messageCount,
                "originalTokens", result.originalTokens(),
                "finalMessageCount", result.messages().size(),
                "finalTokens", result.finalTokens(),
                "wasCompressed", result.wasCompressed(),
                "executedLevels", result.executedLevels(),
                "tokensSaved", result.originalTokens() - result.finalTokens()
        );
    }

    /**
     * 测试 L5 Autocompact（仅计算，不实际调用 LLM）
     *
     * <p>生成模拟消息列表，测试 L5 压缩的分割和预算计算。
     *
     * @param messageCount   消息数量（默认 100）
     * @param includeTools   是否包含工具调用（默认 true）
     * @param sessionId      会话ID（默认 test-session）
     * @return L5 测试结果
     */
    @GetMapping("/compression/autocompact-test")
    public Map<String, Object> testAutocompact(
            @RequestParam(defaultValue = "100") int messageCount,
            @RequestParam(defaultValue = "true") boolean includeTools,
            @RequestParam(defaultValue = "test-session") String sessionId) {

        log.info("[DebugController] 测试 L5 Autocompact, messageCount={}", messageCount);

        // 生成模拟消息列表
        List<ChatMessage> messages = generateMockMessages(messageCount, includeTools);

        // 计算 token 数
        int totalTokens = tokenEstimator.estimate(messages);

        // 检查是否需要压缩
        boolean needsCompact = autocompact.needsCompact(totalTokens);

        // 计算分割点
        SplitResult split = splitCalculator.calculateSplitIndex(messages);

        // 模拟压缩后的 token 数（假设压缩比为 10:1）
        int estimatedCompressedTokens = totalTokens / 10;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMessages", messageCount);
        result.put("totalTokens", totalTokens);
        result.put("triggerThreshold", autocompactConfig.getTriggerThreshold());
        result.put("needsCompact", needsCompact);
        result.put("splitIndex", split.splitIndex());
        result.put("preservedCount", split.preservedCount());
        result.put("preservedTokens", split.preservedTokens());
        result.put("toCompressCount", split.splitIndex());
        result.put("estimatedCompressedTokens", estimatedCompressedTokens);
        result.put("estimatedFinalTokens", estimatedCompressedTokens + split.preservedTokens());
        result.put("circuitBreakerTripped", autocompact.isCircuitBreakerTripped(sessionId));
        result.put("consecutiveFailures", autocompact.getConsecutiveFailures(sessionId));
        return result;
    }

    /**
     * 重置熔断器
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @PostMapping("/compression/reset-circuit-breaker")
    public Map<String, Object> resetCircuitBreaker(@RequestParam String sessionId) {
        log.info("[DebugController] 重置熔断器, sessionId={}", sessionId);
        autocompact.resetCircuitBreaker(sessionId);
        return Map.of(
                "success", true,
                "message", "熔断器已重置",
                "sessionId", sessionId
        );
    }

    // ==================== 私有方法 ====================

    private Map<String, Object> openVikingError(String message, Exception e) {
        String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        log.warn("[DebugController] {}, error={}", message, error);
        return Map.of(
                "success", false,
                "message", message,
                "error", error
        );
    }

    /**
     * 生成模拟消息列表
     *
     * @param count        消息数量
     * @param includeTools 是否包含工具调用
     * @return 模拟消息列表
     */
    private List<ChatMessage> generateMockMessages(int count, boolean includeTools) {
        List<ChatMessage> messages = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            // 交替生成用户消息和 AI 消息
            if (i % 2 == 0) {
                // 用户消息
                String content = "这是第 " + (i / 2 + 1) + " 条用户消息，包含一些测试内容用于模拟真实对话场景。";
                messages.add(UserMessage.from(content));
            } else {
                // AI 消息
                if (includeTools && random.nextDouble() < 0.3) {
                    // 30% 概率生成工具调用
                    String toolCallId = "call_" + UUID.randomUUID().toString().substring(0, 8);
                    String toolName = random.nextBoolean() ? "webSearch" : "fileRead";

                    // AiMessage with tool call
                    messages.add(AiMessage.aiMessage(null, List.of(
                            dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                                    .id(toolCallId)
                                    .name(toolName)
                                    .arguments("{\"query\": \"test query\"}")
                                    .build()
                    )));

                    // ToolExecutionResultMessage
                    messages.add(ToolExecutionResultMessage.from(toolCallId, toolName,
                            "工具 " + toolName + " 的执行结果，包含一些模拟数据。"));
                } else {
                    // 纯文本 AI 消息
                    String content = "这是第 " + (i / 2 + 1) + " 条 AI 回复，模拟真实场景下的助手响应内容。";
                    messages.add(AiMessage.aiMessage(content));
                }
            }
        }

        return messages;
    }
}
