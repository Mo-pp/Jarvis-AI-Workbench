package com.msz.resume.ai.integrations.openviking.core.service;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OpenViking 用户记忆服务。
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>负责把 JARVIS 业务语义转换为 OpenViking 的固定路径</li>
 *     <li>负责约束第一版只读写用户偏好（preferences）目录</li>
 *     <li>负责调用 {@link OpenVikingClient} 完成真正的 HTTP 读写</li>
 * </ul>
 *
 * <p>当前刻意不负责：</p>
 * <ul>
 *     <li>不负责自动识别哪些对话内容应该被记住</li>
 *     <li>不负责 AutoDream、会话同步、画像注入等高级流程</li>
 *     <li>不负责开放任意 URI 读写</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenVikingMemoryService {

    private static final String WRITE_MODE_CREATE = "create";
    private static final String WRITE_MODE_REPLACE = "replace";
    private static final boolean WAIT_FOR_POST_PROCESSING = false;

    private final OpenVikingClient openVikingClient;

    /**
     * 把一条用户偏好写入 OpenViking。
     *
     * <p>第一版固定写入到：</p>
     * <pre>
     * viking://user/{userId}/memories/preferences/{key}.md
     * </pre>
     *
     * <p>写入策略采用“create 优先，必要时 replace 回退”：
     * 第一次写新文件时可以成功创建；如果文件已经存在，则自动改用 replace 覆盖更新。</p>
     *
     * @param userId JARVIS 用户 ID，同时作为 OpenViking user 空间标识的一部分
     * @param key 偏好键名，例如 language-style、reply-format
     * @param content 要保存的偏好内容
     * @return OpenViking 写入结果
     */
    public OpenVikingWriteResponse writePreference(String userId, String key, String content) {
        validateRequiredText(userId, "userId");
        validateRequiredText(key, "key");
        validateRequiredText(content, "content");

        String sanitizedUserId = userId.trim();
        String sanitizedKey = sanitizeKey(key);
        String uri = buildPreferenceUri(sanitizedUserId, sanitizedKey);
        String normalizedContent = content.trim();

        log.info("[OpenVikingMemoryService] 写入用户偏好: userId={}, key={}, uri={}",
                sanitizedUserId, sanitizedKey, uri);

        try {
            return writeWithMode(uri, normalizedContent, WRITE_MODE_CREATE);
        } catch (OpenVikingClientException e) {
            if (shouldFallbackToReplace(e)) {
                log.info("[OpenVikingMemoryService] 偏好文件已存在，回退为 replace 模式: uri={}", uri);
                return writeWithMode(uri, normalizedContent, WRITE_MODE_REPLACE);
            }
            throw e;
        }
    }

    /**
     * 读取一条用户偏好。
     *
     * <p>读取路径与写入路径保持一致：</p>
     * <pre>
     * viking://user/{userId}/memories/preferences/{key}.md
     * </pre>
     *
     * @param userId JARVIS 用户 ID，同时作为 OpenViking user 空间标识的一部分
     * @param key 偏好键名，例如 language-style、reply-format
     * @return OpenViking 读取结果
     */
    public OpenVikingReadResponse readPreference(String userId, String key) {
        validateRequiredText(userId, "userId");
        validateRequiredText(key, "key");

        String sanitizedUserId = userId.trim();
        String sanitizedKey = sanitizeKey(key);
        String uri = buildPreferenceUri(sanitizedUserId, sanitizedKey);

        log.info("[OpenVikingMemoryService] 读取用户偏好: userId={}, key={}, uri={}",
                sanitizedUserId, sanitizedKey, uri);

        return openVikingClient.read(uri, currentIdentity());
    }

    /**
     * 使用指定模式执行一次写入。
     */
    private OpenVikingWriteResponse writeWithMode(String uri, String content, String mode) {
        OpenVikingWriteRequest request = new OpenVikingWriteRequest(
                uri,
                content,
                mode,
                WAIT_FOR_POST_PROCESSING,
                null
        );
        return openVikingClient.write(request, currentIdentity());
    }

    private OpenVikingIdentity currentIdentity() {
        return ToolRuntimeContext.getOpenVikingIdentity();
    }

    /**
     * 判断 create 失败后是否应该回退到 replace。
     *
     * <p>当前只在“文件已存在”这一类场景下回退，避免把其他真正的错误误判为可覆盖写入。</p>
     */
    private boolean shouldFallbackToReplace(OpenVikingClientException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("already exists")
                || normalized.contains("file exists")
                || normalized.contains("conflict")
                || normalized.contains("409");
    }

    /**
     * 构造第一版固定的用户偏好 URI。
     */
    private String buildPreferenceUri(String userId, String key) {
        return "viking://user/" + userId + "/memories/preferences/" + key + ".md";
    }

    /**
     * 对 key 做最小清洗，保证它适合作为文件名的一部分。
     */
    private String sanitizeKey(String key) {
        return key.trim()
                .replace(" ", "-")
                .replace("/", "-")
                .replace("\\", "-");
    }

    /**
     * 校验必填文本字段。
     */
    private void validateRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OpenViking memory operation failed: " + fieldName + " is blank.");
        }
    }
}
