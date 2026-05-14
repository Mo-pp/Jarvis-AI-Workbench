package com.msz.resume.ai.integrations.openviking.core.session;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAppendSessionMessageRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCommitSessionResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCreateSessionResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSessionContextResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * OpenViking Session Gateway 默认实现。
 *
 * <p>遵循最佳努力原则，所有操作失败时返回 false/empty，不阻断主对话流程。</p>
 */
@Slf4j
@Component
public class DefaultOpenVikingSessionGateway implements OpenVikingSessionGateway {

    private final OpenVikingClient client;
    private final OpenVikingSessionProperties properties;
    private final OpenVikingSessionContextFormatter contextFormatter;

    @Autowired
    public DefaultOpenVikingSessionGateway(OpenVikingClient client,
                                           OpenVikingSessionProperties properties,
                                           OpenVikingSessionContextFormatter contextFormatter) {
        this.client = client;
        this.properties = properties;
        this.contextFormatter = contextFormatter;
        log.info("[OpenVikingSessionGateway] 初始化完成: enabled={}, appendUser={}, appendAssistant={}, contextOnCompact={}, manualCommit={}, contextTokenBudget={}",
                properties.isEnabled(),
                properties.isAppendUser(),
                properties.isAppendAssistant(),
                properties.isContextOnCompact(),
                properties.isManualCommit(),
                properties.getContextTokenBudget());
    }

    @Override
    public boolean ensureSession(String sessionId) {
        return ensureSession(sessionId, null);
    }

    @Override
    public boolean ensureSession(String sessionId, OpenVikingIdentity identity) {
        if (!properties.isEnabled()) {
            log.debug("[OpenVikingSessionGateway] ensureSession 跳过: disabled, sessionId={}", sessionId);
            return false;
        }

        if (!hasText(sessionId)) {
            log.warn("[OpenVikingSessionGateway] ensureSession 失败: sessionId 为空");
            return false;
        }

        try {
            OpenVikingCreateSessionResponse response = client.createSession(sessionId, identity);
            if (response != null && "ok".equalsIgnoreCase(response.status())) {
                log.info("[OpenVikingSessionGateway] ensureSession 成功: sessionId={}", sessionId);
                return true;
            }
            log.warn("[OpenVikingSessionGateway] ensureSession 返回非 ok 状态: sessionId={}, status={}",
                    sessionId, response != null ? response.status() : "null");
            return false;
        } catch (OpenVikingClientException e) {
            if (isAlreadyExistsError(e)) {
                log.info("[OpenVikingSessionGateway] ensureSession 成功（已存在）: sessionId={}", sessionId);
                return true;
            }
            log.warn("[OpenVikingSessionGateway] ensureSession 失败: sessionId={}, error={}",
                    sessionId, safeErrorMessage(e));
            return false;
        } catch (Exception e) {
            log.warn("[OpenVikingSessionGateway] ensureSession 异常: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean appendUserMessage(String sessionId, String content) {
        return appendUserMessage(sessionId, content, null);
    }

    @Override
    public boolean appendUserMessage(String sessionId, String content, OpenVikingIdentity identity) {
        if (!properties.isEnabled() || !properties.isAppendUser()) {
            log.debug("[OpenVikingSessionGateway] appendUserMessage 跳过: disabled, sessionId={}", sessionId);
            return false;
        }

        return appendMessage(sessionId, "user", content, identity);
    }

    @Override
    public boolean appendAssistantMessage(String sessionId, String content) {
        return appendAssistantMessage(sessionId, content, null);
    }

    @Override
    public boolean appendAssistantMessage(String sessionId, String content, OpenVikingIdentity identity) {
        if (!properties.isEnabled() || !properties.isAppendAssistant()) {
            log.debug("[OpenVikingSessionGateway] appendAssistantMessage 跳过: disabled, sessionId={}", sessionId);
            return false;
        }

        return appendMessage(sessionId, "assistant", content, identity);
    }

    @Override
    public Optional<String> loadSessionContext(String sessionId, Integer tokenBudget) {
        return loadSessionContext(sessionId, tokenBudget, null);
    }

    @Override
    public Optional<String> loadSessionContext(String sessionId, Integer tokenBudget, OpenVikingIdentity identity) {
        if (!properties.isEnabled() || !properties.isContextOnCompact()) {
            log.debug("[OpenVikingSessionGateway] loadSessionContext 跳过: disabled, sessionId={}", sessionId);
            return Optional.empty();
        }

        if (!hasText(sessionId)) {
            log.warn("[OpenVikingSessionGateway] loadSessionContext 失败: sessionId 为空");
            return Optional.empty();
        }

        try {
            Integer budget = tokenBudget != null ? tokenBudget : properties.getContextTokenBudget();
            OpenVikingSessionContextResponse response = client.getSessionContext(sessionId, budget, identity);

            if (response == null || !"ok".equalsIgnoreCase(response.status())) {
                log.warn("[OpenVikingSessionGateway] loadSessionContext 返回非 ok 状态: sessionId={}, status={}",
                        sessionId, response != null ? response.status() : "null");
                return Optional.empty();
            }

            String formattedContext = contextFormatter.format(response, budget);
            if (!hasText(formattedContext)) {
                log.debug("[OpenVikingSessionGateway] loadSessionContext 为空: sessionId={}", sessionId);
                return Optional.empty();
            }

            log.info("[OpenVikingSessionGateway] loadSessionContext 成功: sessionId={}, length={}",
                    sessionId, formattedContext.length());
            return Optional.of(formattedContext);

        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSessionGateway] loadSessionContext 失败: sessionId={}, error={}",
                    sessionId, safeErrorMessage(e));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[OpenVikingSessionGateway] loadSessionContext 异常: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> commitSession(String sessionId) {
        return commitSession(sessionId, null);
    }

    @Override
    public Optional<String> commitSession(String sessionId, OpenVikingIdentity identity) {
        if (!properties.isEnabled() || !properties.isManualCommit()) {
            log.debug("[OpenVikingSessionGateway] commitSession 跳过: disabled, sessionId={}", sessionId);
            return Optional.empty();
        }

        if (!hasText(sessionId)) {
            log.warn("[OpenVikingSessionGateway] commitSession 失败: sessionId 为空");
            return Optional.empty();
        }

        try {
            OpenVikingCommitSessionResponse response = client.commitSession(sessionId, identity);

            if (response == null || !"ok".equalsIgnoreCase(response.status())) {
                log.warn("[OpenVikingSessionGateway] commitSession 返回非 ok 状态: sessionId={}, status={}",
                        sessionId, response != null ? response.status() : "null");
                return Optional.empty();
            }

            String taskId = extractTaskId(response);
            log.info("[OpenVikingSessionGateway] commitSession 成功: sessionId={}, taskId={}", sessionId, taskId);
            return Optional.ofNullable(taskId);

        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSessionGateway] commitSession 失败: sessionId={}, error={}",
                    sessionId, safeErrorMessage(e));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[OpenVikingSessionGateway] commitSession 异常: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    // ==================== Private Methods ====================

    /**
     * 追加消息到 OpenViking Session（内部方法）。
     */
    private boolean appendMessage(String sessionId, String role, String content, OpenVikingIdentity identity) {
        if (!hasText(sessionId)) {
            log.warn("[OpenVikingSessionGateway] appendMessage 失败: sessionId 为空, role={}", role);
            return false;
        }

        if (!hasText(content)) {
            log.debug("[OpenVikingSessionGateway] appendMessage 跳过: content 为空, sessionId={}, role={}", sessionId, role);
            return true; // 空内容视为成功，不追加
        }

        try {
            OpenVikingAppendSessionMessageRequest request = buildSimpleMessageRequest(role, content);
            var response = client.appendSessionMessage(sessionId, request, identity);

            if (response != null && "ok".equalsIgnoreCase(response.status())) {
                log.debug("[OpenVikingSessionGateway] appendMessage 成功: sessionId={}, role={}", sessionId, role);
                return true;
            }

            log.warn("[OpenVikingSessionGateway] appendMessage 返回非 ok 状态: sessionId={}, role={}, status={}",
                    sessionId, role, response != null ? response.status() : "null");
            return false;

        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSessionGateway] appendMessage 失败: sessionId={}, role={}, error={}",
                    sessionId, role, safeErrorMessage(e));
            return false;
        } catch (Exception e) {
            log.warn("[OpenVikingSessionGateway] appendMessage 异常: sessionId={}, role={}, error={}",
                    sessionId, role, e.getMessage());
            return false;
        }
    }

    /**
     * 构建 Simple Message 请求体。
     */
    private OpenVikingAppendSessionMessageRequest buildSimpleMessageRequest(String role, String content) {
        return new OpenVikingAppendSessionMessageRequest(
                role,
                null, // roleId: 暂不使用
                content,
                null, // parts: Simple 模式不使用
                Instant.now().toString()
        );
    }

    /**
     * 判断是否为"已存在"错误。
     */
    private boolean isAlreadyExistsError(OpenVikingClientException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("ALREADY_EXISTS")
                || message.contains("already exists")
                || message.contains("已存在");
    }

    /**
     * 从 commit 响应中提取 task_id。
     */
    private String extractTaskId(OpenVikingCommitSessionResponse response) {
        if (response == null || response.result() == null) {
            return null;
        }
        return response.result().taskId();
    }

    /**
     * 安全提取错误消息，不暴露敏感信息。
     */
    private String safeErrorMessage(OpenVikingClientException e) {
        String message = e.getMessage();
        if (message == null) {
            return "unknown error";
        }
        // 截断过长消息
        return truncateText(message, 200);
    }

    private String truncateText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
