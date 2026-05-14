package com.msz.resume.ai.integrations.openviking.core.client;

import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAddResourceRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAddResourceResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAppendSessionMessageRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAppendSessionMessageResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAdminCreateAccountRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAdminCreateAccountResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCommitSessionResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCreateSessionRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCreateSessionResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSessionContextResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingTaskResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingTempUploadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteResponse;
import com.msz.resume.ai.integrations.openviking.core.context.OpenVikingIdentityContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * OpenViking HTTP 客户端。
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>负责发起 OpenViking HTTP 请求</li>
 *     <li>负责统一附加认证与租户请求头</li>
 *     <li>负责把 HTTP/网络/业务错误转换为统一的 {@link OpenVikingClientException}</li>
 * </ul>
 *
 * <p>不负责：</p>
 * <ul>
 *     <li>不负责业务级 URI 组装</li>
 *     <li>不负责决定写入/检索哪类记忆</li>
 *     <li>不负责上层 Tool 或 Service 的业务编排</li>
 * </ul>
 */
@Slf4j
@Component
public class OpenVikingClient {

    private final OpenVikingProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenVikingClient(OpenVikingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .requestFactory(buildRequestFactory(properties))
                .build();
        log.info("[OpenVikingClient] 初始化完成: baseUrl={}, timeout={}, apiKeyConfigured={}, accountConfigured={}, userConfigured={}, agentConfigured={}",
                trimTrailingSlash(properties.getBaseUrl()),
                properties.getTimeout(),
                hasText(properties.getApiKey()),
                hasText(properties.getAccount()),
                hasText(properties.getUser()),
                hasText(properties.getAgent()));
    }

    public OpenVikingClient(OpenVikingProperties properties) {
        this(properties, new ObjectMapper());
    }

    public OpenVikingAdminCreateAccountResponse createAdminAccount(OpenVikingAdminCreateAccountRequest request) {
        requireApiKey();
        if (request == null) {
            throw new OpenVikingClientException("OpenViking create admin account failed: request is null.");
        }
        try {
            OpenVikingAdminCreateAccountResponse response = restClient.post()
                    .uri("/api/v1/admin/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingAdminCreateAccountResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking create admin account returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                String message = response.error() != null ? response.error().message() : "unknown error";
                throw new OpenVikingClientException("OpenViking create admin account failed: " + message);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("create admin account", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("create admin account", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking create admin account request failed: " + safeMessage(e), e);
        }
    }

    /**
     * 调用 OpenViking 健康检查接口。
     */
    public String health() {
        try {
            return restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new OpenVikingClientException("OpenViking health check failed: HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw mapAccessException("health check", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking health check failed.", e);
        }
    }

    /**
     * 调用 OpenViking 系统状态接口。
     */
    public String status() {
        requireApiKey();
        try {
            return restClient.get()
                    .uri("/api/v1/system/status")
                    .headers(addAuthHeaders())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw mapResponseException("status", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("status", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking status request failed.", e);
        }
    }

    /**
     * 创建指定 ID 的 OpenViking 会话。
     */
    public OpenVikingCreateSessionResponse createSession(String sessionId) {
        requireApiKey();
        if (!hasText(sessionId)) {
            throw new OpenVikingClientException("OpenViking create session failed: sessionId is empty.");
        }
        try {
            OpenVikingCreateSessionResponse response = restClient.post()
                    .uri("/api/v1/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(new OpenVikingCreateSessionRequest(sessionId))
                    .retrieve()
                    .body(OpenVikingCreateSessionResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking create session returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapCreateSessionBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("create session", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("create session", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking create session request failed.", e);
        }
    }

    public OpenVikingCreateSessionResponse createSession(String sessionId, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> createSession(sessionId));
    }

    /**
     * 向指定 OpenViking 会话追加单条消息。
     */
    public OpenVikingAppendSessionMessageResponse appendSessionMessage(String sessionId, OpenVikingAppendSessionMessageRequest request) {
        requireApiKey();
        if (!hasText(sessionId)) {
            throw new OpenVikingClientException("OpenViking append session message failed: sessionId is empty.");
        }
        if (request == null) {
            throw new OpenVikingClientException("OpenViking append session message failed: request is null.");
        }
        try {
            OpenVikingAppendSessionMessageResponse response = restClient.post()
                    .uri("/api/v1/sessions/{sessionId}/messages", sessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingAppendSessionMessageResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking append session message returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapAppendSessionMessageBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("append session message", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("append session message", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking append session message request failed.", e);
        }
    }

    public OpenVikingAppendSessionMessageResponse appendSessionMessage(
            String sessionId,
            OpenVikingAppendSessionMessageRequest request,
            OpenVikingIdentity identity) {
        return withIdentity(identity, () -> appendSessionMessage(sessionId, request));
    }

    /**
     * 使用 OpenViking 服务端默认预算读取指定会话上下文。
     */
    public OpenVikingSessionContextResponse getSessionContext(String sessionId) {
        return getSessionContext(sessionId, null);
    }

    /**
     * 使用显式 tokenBudget 读取指定会话上下文。
     */
    public OpenVikingSessionContextResponse getSessionContext(String sessionId, Integer tokenBudget) {
        requireApiKey();
        if (!hasText(sessionId)) {
            throw new OpenVikingClientException("OpenViking get session context failed: sessionId is empty.");
        }
        try {
            OpenVikingSessionContextResponse response = restClient.get()
                    .uri(builder -> {
                        var uriBuilder = builder.path("/api/v1/sessions/{sessionId}/context");
                        if (tokenBudget != null) {
                            uriBuilder.queryParam("token_budget", tokenBudget);
                        }
                        return uriBuilder.build(sessionId);
                    })
                    .headers(addAuthHeaders())
                    .retrieve()
                    .body(OpenVikingSessionContextResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking get session context returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapSessionContextBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("get session context", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("get session context", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking get session context request failed.", e);
        }
    }

    public OpenVikingSessionContextResponse getSessionContext(String sessionId, Integer tokenBudget, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> getSessionContext(sessionId, tokenBudget));
    }

    /**
     * 提交指定 OpenViking 会话，不轮询后台任务。
     */
    public OpenVikingCommitSessionResponse commitSession(String sessionId) {
        requireApiKey();
        if (!hasText(sessionId)) {
            throw new OpenVikingClientException("OpenViking commit session failed: sessionId is empty.");
        }
        try {
            OpenVikingCommitSessionResponse response = restClient.post()
                    .uri("/api/v1/sessions/{sessionId}/commit", sessionId)
                    .headers(addAuthHeaders())
                    .retrieve()
                    .body(OpenVikingCommitSessionResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking commit session returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapCommitSessionBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("commit session", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("commit session", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking commit session request failed.", e);
        }
    }

    public OpenVikingCommitSessionResponse commitSession(String sessionId, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> commitSession(sessionId));
    }

    /**
     * 查询一次 OpenViking 后台任务状态，不轮询。
     */
    public OpenVikingTaskResponse getTask(String taskId) {
        requireApiKey();
        if (!hasText(taskId)) {
            throw new OpenVikingClientException("OpenViking get task failed: taskId is empty.");
        }
        try {
            OpenVikingTaskResponse response = restClient.get()
                    .uri("/api/v1/tasks/{taskId}", taskId)
                    .headers(addAuthHeaders())
                    .retrieve()
                    .body(OpenVikingTaskResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking get task returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapTaskBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("get task", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("get task", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking get task request failed.", e);
        }
    }

    public OpenVikingTaskResponse getTask(String taskId, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> getTask(taskId));
    }

    /**
     * 调用 OpenViking 语义检索接口（/api/v1/search/find）。
     */
    public OpenVikingFindResponse find(OpenVikingFindRequest request) {
        requireApiKey();
        try {
            OpenVikingFindResponse response = restClient.post()
                    .uri("/api/v1/search/find")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingFindResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking search returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapFindBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("search", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("search", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking search request failed.", e);
        }
    }

    public OpenVikingFindResponse find(OpenVikingFindRequest request, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> find(request));
    }

    /**
     * 调用 OpenViking session-aware 语义检索接口（/api/v1/search/search）。
     */
    public OpenVikingSearchResponse search(OpenVikingSearchRequest request) {
        requireApiKey();
        if (request == null) {
            throw new OpenVikingClientException("OpenViking session search failed: request is null.");
        }
        try {
            OpenVikingSearchResponse response = restClient.post()
                    .uri("/api/v1/search/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingSearchResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking session search returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapSearchBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("session search", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("session search", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking session search request failed.", e);
        }
    }

    public OpenVikingSearchResponse search(OpenVikingSearchRequest request, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> search(request));
    }

    /**
     * 调用 OpenViking 内容模式匹配接口（/api/v1/search/grep）。
     */
    public OpenVikingReadResponse grep(String uri, String pattern, Boolean caseInsensitive, Integer nodeLimit) {
        requireApiKey();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("uri", uri);
        request.put("pattern", pattern);
        request.put("case_insensitive", Boolean.TRUE.equals(caseInsensitive));
        if (nodeLimit != null) {
            request.put("node_limit", nodeLimit);
        }
        try {
            OpenVikingReadResponse response = restClient.post()
                    .uri("/api/v1/search/grep")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingReadResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking grep returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapReadBusinessError(response, "grep");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("grep", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("grep", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking grep request failed.", e);
        }
    }

    public OpenVikingReadResponse grep(String uri, String pattern, Boolean caseInsensitive, Integer nodeLimit, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> grep(uri, pattern, caseInsensitive, nodeLimit));
    }

    /**
     * 调用 OpenViking 路径模式匹配接口（/api/v1/search/glob）。
     */
    public OpenVikingReadResponse glob(String pattern, String uri, Integer nodeLimit) {
        requireApiKey();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("pattern", pattern);
        request.put("uri", uri);
        if (nodeLimit != null) {
            request.put("node_limit", nodeLimit);
        }
        try {
            OpenVikingReadResponse response = restClient.post()
                    .uri("/api/v1/search/glob")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingReadResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking glob returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapReadBusinessError(response, "glob");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("glob", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("glob", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking glob request failed.", e);
        }
    }

    public OpenVikingReadResponse glob(String pattern, String uri, Integer nodeLimit, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> glob(pattern, uri, nodeLimit));
    }

    /**
     * 调用 OpenViking 文本读取接口（/api/v1/content/read）。
     *
     * <p>用于按完整 URI 读取文本内容，当前主要服务于读取用户偏好。</p>
     */
    public OpenVikingReadResponse read(String uri) {
        return readContentEndpoint("/api/v1/content/read", uri, "read");
    }

    public OpenVikingReadResponse read(String uri, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> read(uri));
    }

    /**
     * 调用 OpenViking L0 摘要读取接口（/api/v1/content/abstract）。
     */
    public OpenVikingReadResponse readAbstract(String uri) {
        return readContentEndpoint("/api/v1/content/abstract", uri, "read abstract");
    }

    public OpenVikingReadResponse readAbstract(String uri, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> readAbstract(uri));
    }

    /**
     * 调用 OpenViking L1 概览读取接口（/api/v1/content/overview）。
     */
    public OpenVikingReadResponse readOverview(String uri) {
        return readContentEndpoint("/api/v1/content/overview", uri, "read overview");
    }

    public OpenVikingReadResponse readOverview(String uri, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> readOverview(uri));
    }

    /**
     * 调用 OpenViking 扁平目录浏览接口（/api/v1/fs/ls）。
     */
    public OpenVikingReadResponse list(String uri, Integer nodeLimit) {
        requireApiKey();
        try {
            OpenVikingReadResponse response = restClient.get()
                    .uri(builder -> {
                        var uriBuilder = builder.path("/api/v1/fs/ls")
                                .queryParam("uri", uri)
                                .queryParam("simple", false)
                                .queryParam("recursive", false)
                                .queryParam("output", "original")
                                .queryParam("abs_limit", 256)
                                .queryParam("show_all_hidden", false);
                        if (nodeLimit != null) {
                            uriBuilder.queryParam("node_limit", nodeLimit);
                        }
                        return uriBuilder.build();
                    })
                    .headers(addAuthHeaders())
                    .retrieve()
                    .body(OpenVikingReadResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking list returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapReadBusinessError(response, "list");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("list", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("list", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking list request failed.", e);
        }
    }

    public OpenVikingReadResponse list(String uri, Integer nodeLimit, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> list(uri, nodeLimit));
    }

    /**
     * 调用 OpenViking 层级目录浏览接口（/api/v1/fs/tree）。
     */
    public OpenVikingReadResponse tree(String uri, Integer nodeLimit) {
        requireApiKey();
        try {
            OpenVikingReadResponse response = restClient.get()
                    .uri(builder -> {
                        var uriBuilder = builder.path("/api/v1/fs/tree")
                                .queryParam("uri", uri)
                                .queryParam("output", "original")
                                .queryParam("abs_limit", 128)
                                .queryParam("show_all_hidden", false);
                        if (nodeLimit != null) {
                            uriBuilder.queryParam("node_limit", nodeLimit);
                        }
                        return uriBuilder.build();
                    })
                    .headers(addAuthHeaders())
                    .retrieve()
                    .body(OpenVikingReadResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking tree returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapReadBusinessError(response, "tree");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("tree", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("tree", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking tree request failed.", e);
        }
    }

    public OpenVikingReadResponse tree(String uri, Integer nodeLimit, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> tree(uri, nodeLimit));
    }

    private OpenVikingReadResponse readContentEndpoint(String path, String uri, String operation) {
        requireApiKey();
        try {
            OpenVikingReadResponse response = restClient.get()
                    .uri(builder -> builder.path(path).queryParam("uri", uri).build())
                    .headers(addAuthHeaders())
                    .retrieve()
                    .body(OpenVikingReadResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking " + operation + " returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapReadBusinessError(response, operation);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException(operation, e);
        } catch (ResourceAccessException e) {
            throw mapAccessException(operation, e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking " + operation + " request failed.", e);
        }
    }

    /**
     * 调用 OpenViking Skill 添加接口（/api/v1/skills）。
     */
    public OpenVikingSkillAddResponse addSkill(OpenVikingSkillAddRequest request) {
        requireApiKey();
        if (request == null) {
            throw new OpenVikingClientException("OpenViking add skill failed: request is null.");
        }
        try {
            OpenVikingSkillAddResponse response = restClient.post()
                    .uri("/api/v1/skills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingSkillAddResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking add skill returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapAddSkillBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("add skill", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("add skill", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking add skill request failed.", e);
        }
    }

    public OpenVikingSkillAddResponse addSkill(OpenVikingSkillAddRequest request, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> addSkill(request));
    }

    /**
     * 调用 OpenViking 资源添加接口（POST /api/v1/resources）。
     *
     * <p>支持以下资源来源：</p>
     * <ul>
     *     <li>远程URL（path参数）：HTTP(S) URL、Git仓库URL、raw文件URL等</li>
     *     <li>本地文件（temp_file_id参数）：通过temp_upload上传后的临时文件ID</li>
     * </ul>
     *
     * @param request 资源添加请求
     * @return 资源添加响应
     */
    public OpenVikingAddResourceResponse addResource(OpenVikingAddResourceRequest request) {
        requireApiKey();
        if (request == null) {
            throw new OpenVikingClientException("OpenViking add resource failed: request is null.");
        }
        if (request.path() == null && request.tempFileId() == null) {
            throw new OpenVikingClientException("OpenViking add resource failed: either path or temp_file_id is required.");
        }
        try {
            OpenVikingAddResourceResponse response = restClient.post()
                    .uri("/api/v1/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .body(OpenVikingAddResourceResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking add resource returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapAddResourceBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("add resource", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("add resource", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking add resource request failed.", e);
        }
    }

    public OpenVikingAddResourceResponse addResource(OpenVikingAddResourceRequest request, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> addResource(request));
    }

    /**
     * 调用 OpenViking 临时文件上传接口（/api/v1/resources/temp_upload）。
     */
    public OpenVikingTempUploadResponse tempUpload(String filename, byte[] content, String contentType) {
        requireApiKey();
        if (!hasText(filename)) {
            throw new OpenVikingClientException("OpenViking temp upload failed: filename is empty.");
        }
        if (content == null || content.length == 0) {
            throw new OpenVikingClientException("OpenViking temp upload failed: content is empty.");
        }
        try {
            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            OpenVikingTempUploadResponse response = restClient.post()
                    .uri("/api/v1/resources/temp_upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(addAuthHeaders())
                    .body(body)
                    .retrieve()
                    .body(OpenVikingTempUploadResponse.class);

            if (response == null) {
                throw new OpenVikingClientException("OpenViking temp upload returned empty response.");
            }
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapTempUploadBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw mapResponseException("temp upload", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("temp upload", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking temp upload request failed.", e);
        }
    }

    public OpenVikingTempUploadResponse tempUpload(String filename, byte[] content, String contentType, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> tempUpload(filename, content, contentType));
    }

    /**
     * 调用 OpenViking 资源删除接口（DELETE /api/v1/fs）。
     */
    public void remove(String uri, boolean recursive) {
        requireApiKey();
        if (!hasText(uri)) {
            throw new OpenVikingClientException("OpenViking remove failed: uri is empty.");
        }
        try {
            restClient.delete()
                    .uri(builder -> builder.path("/api/v1/fs")
                            .queryParam("uri", uri)
                            .queryParam("recursive", recursive)
                            .build())
                    .headers(addAuthHeaders())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw mapResponseException("remove", e);
        } catch (ResourceAccessException e) {
            throw mapAccessException("remove", e);
        } catch (RestClientException e) {
            throw new OpenVikingClientException("OpenViking remove request failed.", e);
        }
    }

    public void remove(String uri, boolean recursive, OpenVikingIdentity identity) {
        withIdentity(identity, () -> {
            remove(uri, recursive);
            return null;
        });
    }

    /**
     * 调用 OpenViking 文本写入接口（/api/v1/content/write）。
     */
    public OpenVikingWriteResponse write(OpenVikingWriteRequest request) {
        requireApiKey();
        try {
            ResponseEntity<byte[]> responseEntity = restClient.post()
                    .uri("/api/v1/content/write")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(addAuthHeaders())
                    .body(request)
                    .retrieve()
                    .toEntity(byte[].class);

            String responseBody = responseEntity.getBody() == null
                    ? ""
                    : new String(responseEntity.getBody(), StandardCharsets.UTF_8);
            logWriteResponse(request, responseEntity, responseBody);
            OpenVikingWriteResponse response = parseWriteResponse(responseBody, responseEntity.getHeaders().getContentType());
            if (response.error() != null || "error".equalsIgnoreCase(response.status())) {
                throw mapWriteBusinessError(response);
            }
            return response;
        } catch (RestClientResponseException e) {
            logOpenVikingFailure("write", "/api/v1/content/write", requestSummary(request), e.getClass().getName(), e.getMessage(), e.getResponseBodyAsString());
            throw mapResponseException("write", e);
        } catch (ResourceAccessException e) {
            logOpenVikingFailure("write", "/api/v1/content/write", requestSummary(request), e.getClass().getName(), e.getMessage(), null);
            throw mapAccessException("write", e);
        } catch (RestClientException e) {
            logOpenVikingFailure("write", "/api/v1/content/write", requestSummary(request), e.getClass().getName(), e.getMessage(), null);
            throw new OpenVikingClientException("OpenViking write request failed: " + safeMessage(e), e);
        }
    }

    public OpenVikingWriteResponse write(OpenVikingWriteRequest request, OpenVikingIdentity identity) {
        return withIdentity(identity, () -> write(request));
    }

    /**
     * 统一附加 OpenViking 认证与租户上下文请求头。
     */
    private Consumer<org.springframework.http.HttpHeaders> addAuthHeaders() {
        return headers -> {
            headers.set("X-API-Key", properties.getApiKey());
            OpenVikingIdentity identity = OpenVikingIdentityContextHolder.get();
            String account = identity != null ? identity.account() : properties.getAccount();
            String user = identity != null ? identity.user() : properties.getUser();
            String agent = identity != null ? identity.agent() : properties.getAgent();
            if (hasText(account)) {
                headers.set("X-OpenViking-Account", account);
            }
            if (hasText(user)) {
                headers.set("X-OpenViking-User", user);
            }
            if (hasText(agent)) {
                headers.set("X-OpenViking-Agent", agent);
            }
        };
    }

    private <T> T withIdentity(OpenVikingIdentity identity, Supplier<T> supplier) {
        OpenVikingIdentity previous = OpenVikingIdentityContextHolder.get();
        try {
            if (identity != null) {
                OpenVikingIdentityContextHolder.set(identity);
            }
            return supplier.get();
        } finally {
            if (previous != null) {
                OpenVikingIdentityContextHolder.set(previous);
            } else {
                OpenVikingIdentityContextHolder.clear();
            }
        }
    }

    /**
     * 校验 API Key 是否存在。
     */
    private void requireApiKey() {
        if (!hasText(properties.getApiKey())) {
            throw new OpenVikingClientException("OpenViking API key is not configured. Set OPENVIKING_API_KEY.");
        }
    }

    private OpenVikingClientException mapCreateSessionBusinessError(OpenVikingCreateSessionResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking create session failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking create session failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking create session failed: " + message);
    }

    private OpenVikingClientException mapAppendSessionMessageBusinessError(OpenVikingAppendSessionMessageResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking append session message failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking append session message failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking append session message failed: " + message);
    }

    private OpenVikingClientException mapSessionContextBusinessError(OpenVikingSessionContextResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking get session context failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking get session context failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking get session context failed: " + message);
    }

    private OpenVikingClientException mapCommitSessionBusinessError(OpenVikingCommitSessionResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking commit session failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking commit session failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking commit session failed: " + message);
    }

    private OpenVikingClientException mapTaskBusinessError(OpenVikingTaskResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking get task failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking get task failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking get task failed: " + message);
    }

    private OpenVikingClientException mapFindBusinessError(OpenVikingFindResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking search failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking search failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking search failed: " + message);
    }

    private OpenVikingClientException mapSearchBusinessError(OpenVikingSearchResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking session search failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking session search failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking session search failed: " + message);
    }

    private OpenVikingClientException mapAddSkillBusinessError(OpenVikingSkillAddResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking add skill failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking add skill failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking add skill failed: " + message);
    }

    private OpenVikingClientException mapAddResourceBusinessError(OpenVikingAddResourceResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking add resource failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking add resource failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking add resource failed: " + message);
    }

    private OpenVikingClientException mapTempUploadBusinessError(OpenVikingTempUploadResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking temp upload failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking temp upload failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking temp upload failed: " + message);
    }

    private OpenVikingClientException mapReadBusinessError(OpenVikingReadResponse response) {
        return mapReadBusinessError(response, "read");
    }

    private OpenVikingClientException mapReadBusinessError(OpenVikingReadResponse response, String operation) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking " + operation + " failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking " + operation + " failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking " + operation + " failed: " + message);
    }

    private OpenVikingClientException mapWriteBusinessError(OpenVikingWriteResponse response) {
        String message = response.error() != null ? response.error().message() : null;
        if (!hasText(message)) {
            return new OpenVikingClientException("OpenViking write failed.");
        }
        if (message.contains("ROOT requests to tenant-scoped APIs")) {
            return new OpenVikingClientException(
                    "OpenViking write failed: current key requires tenant headers. Set OPENVIKING_ACCOUNT and OPENVIKING_USER, or use a normal user key."
            );
        }
        return new OpenVikingClientException("OpenViking write failed: " + message);
    }

    private OpenVikingClientException mapResponseException(String operation, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String responseBody = e.getResponseBodyAsString();
        String responseBodySuffix = buildResponseBodySuffix(responseBody);
        if (status == 401 || status == 403) {
            return new OpenVikingClientException("OpenViking authentication failed. Check API key or tenant headers" + responseBodySuffix);
        }
        if (status >= 400 && status < 500) {
            return new OpenVikingClientException("OpenViking " + operation + " request was rejected: HTTP " + status + responseBodySuffix);
        }
        return new OpenVikingClientException("OpenViking service returned server error: HTTP " + status + responseBodySuffix);
    }

    private OpenVikingClientException mapAccessException(String operation, ResourceAccessException e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (message.contains("timed out") || message.contains("timeout")) {
            return new OpenVikingClientException("OpenViking " + operation + " request timed out.", e);
        }
        return new OpenVikingClientException("OpenViking service is unreachable: " + properties.getBaseUrl(), e);
    }

    private static SimpleClientHttpRequestFactory buildRequestFactory(OpenVikingProperties properties) {
        int timeoutMillis = Math.toIntExact(properties.getTimeout().toMillis());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return requestFactory;
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private OpenVikingWriteResponse parseWriteResponse(String responseBody, MediaType contentType) {
        if (!hasText(responseBody)) {
            throw new OpenVikingClientException("OpenViking write returned empty response. contentType=" + contentType);
        }
        try {
            OpenVikingWriteResponse response = objectMapper.readValue(responseBody, OpenVikingWriteResponse.class);
            if (response == null) {
                throw new OpenVikingClientException("OpenViking write returned empty response. contentType=" + contentType);
            }
            return response;
        } catch (JsonProcessingException e) {
            log.warn("[OpenVikingClient] OpenViking write 响应解析失败: contentType={}, body={}, error={}",
                    contentType, limitText(responseBody.trim(), 1200), e.getMessage());
            throw new OpenVikingClientException(
                    "OpenViking write response parse failed. contentType=" + contentType
                            + ", body=" + limitText(responseBody.trim(), 800), e);
        }
    }

    private void logWriteResponse(OpenVikingWriteRequest request, ResponseEntity<byte[]> responseEntity, String responseBody) {
        log.info("[OpenVikingClient] OpenViking write 返回: statusCode={}, contentType={}, request={}, bodyPreview={}",
                responseEntity.getStatusCode().value(),
                responseEntity.getHeaders().getContentType(),
                requestSummary(request),
                limitText(responseBody, 500));
    }

    private void logOpenVikingFailure(String operation, String endpoint, String requestSummary,
                                      String exceptionType, String message, String responseBody) {
        log.warn("[OpenVikingClient] OpenViking 请求失败: operation={}, endpoint={}, baseUrl={}, timeout={}, tenant={}, request={}, exceptionType={}, message={}, responseBody={}",
                operation,
                endpoint,
                trimTrailingSlash(properties.getBaseUrl()),
                properties.getTimeout(),
                tenantSummary(),
                requestSummary,
                exceptionType,
                safeLogText(message, 800),
                safeLogText(responseBody, 1200));
    }

    private String requestSummary(OpenVikingWriteRequest request) {
        if (request == null) {
            return "null";
        }
        return "uri=" + request.uri()
                + ", mode=" + request.mode()
                + ", wait=" + request.waitForProcessing()
                + ", timeout=" + request.timeout()
                + ", contentChars=" + (request.content() != null ? request.content().length() : 0);
    }

    private String tenantSummary() {
        return "apiKeyConfigured=" + hasText(properties.getApiKey())
                + ", accountConfigured=" + hasText(properties.getAccount())
                + ", userConfigured=" + hasText(properties.getUser())
                + ", agentConfigured=" + hasText(properties.getAgent());
    }

    private static String safeLogText(String value, int maxChars) {
        if (!hasText(value)) {
            return "";
        }
        return limitText(value.trim(), maxChars);
    }

    private static String buildResponseBodySuffix(String responseBody) {
        if (!hasText(responseBody)) {
            return ".";
        }
        return ". Response body: " + limitText(responseBody.trim(), 800);
    }

    private static String safeMessage(Exception e) {
        if (e == null || !hasText(e.getMessage())) {
            return "unknown error";
        }
        return limitText(e.getMessage().trim(), 800);
    }

    private static String limitText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...[truncated]";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
