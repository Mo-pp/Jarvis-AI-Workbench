package com.msz.resume.ai.integrations.openviking.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAppendSessionMessageRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAppendSessionMessageResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCommitSessionResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingCreateSessionResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSessionContextResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingTaskResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingTempUploadResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenVikingClient HTTP 契约测试。
 */
class OpenVikingClientTest {

    private TestHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("createSession 应发送正确路径、请求头和 session_id")
    void shouldCreateSessionWithExpectedHttpContract() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"session_id":"jarvis-session-001","user":{"account_id":"acc-1","user_id":"user-1","agent_id":"jarvis"}}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingCreateSessionResponse response = client.createSession("jarvis-session-001");

        assertEquals("ok", response.status());
        assertEquals("jarvis-session-001", response.result().sessionId());
        assertEquals("acc-1", response.result().user().get("account_id"));
        TestHttpServer.RecordedRequest request = server.singleRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/v1/sessions", request.path());
        assertTrue(request.header("Content-Type").startsWith("application/json"));
        assertEquals("test-key", request.header("X-api-key"));
        assertEquals("acc-1", request.header("X-openviking-account"));
        assertEquals("user-1", request.header("X-openviking-user"));
        assertEquals("jarvis", request.header("X-openviking-agent"));
        assertTrue(request.body().contains("\"session_id\":\"jarvis-session-001\""));
    }

    @Test
    @DisplayName("显式 OpenVikingIdentity 应优先于静态配置请求头")
    void shouldUseExplicitIdentityBeforeStaticFallback() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"session_id":"session-mo","user":{"account_id":"mo","user_id":"mo","agent_id":"jarvis"}}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        client.createSession("session-mo", new OpenVikingIdentity("mo", "mo", "jarvis"));

        TestHttpServer.RecordedRequest request = server.singleRequest();
        assertEquals("mo", request.header("X-openviking-account"));
        assertEquals("mo", request.header("X-openviking-user"));
        assertEquals("jarvis", request.header("X-openviking-agent"));
    }

    @Test
    @DisplayName("createSession 遇到业务错误应抛出客户端异常")
    void shouldMapCreateSessionBusinessError() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"error","error":{"code":"INVALID_ARGUMENT","message":"session_id is invalid"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.createSession("bad-session")
        );

        assertEquals("OpenViking create session failed: session_id is invalid", exception.getMessage());
    }

    @Test
    @DisplayName("createSession 遇到认证错误应抛出认证异常")
    void shouldMapCreateSessionAuthenticationError() throws Exception {
        server = TestHttpServer.start(403, """
                {"status":"error","error":{"code":"FORBIDDEN","message":"tenant headers required"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.createSession("jarvis-session-001")
        );

        assertTrue(exception.getMessage().startsWith("OpenViking authentication failed. Check API key or tenant headers"));
    }

    @Test
    @DisplayName("createSession 缺少 sessionId 应直接失败")
    void shouldRejectBlankSessionId() throws Exception {
        server = TestHttpServer.start(200, "{} ");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.createSession("   ")
        );

        assertEquals("OpenViking create session failed: sessionId is empty.", exception.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    @DisplayName("appendSessionMessage 应发送 content 模式消息")
    void shouldAppendSessionMessageWithContentMode() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"session_id":"jarvis-session-001","message_count":1}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingAppendSessionMessageRequest request = new OpenVikingAppendSessionMessageRequest(
                "user",
                "user-1",
                "你好，JARVIS",
                null,
                "2026-04-30T20:00:00Z"
        );

        OpenVikingAppendSessionMessageResponse response = client.appendSessionMessage("jarvis-session-001", request);

        assertEquals("ok", response.status());
        assertEquals("jarvis-session-001", response.result().sessionId());
        assertEquals(1, response.result().messageCount());
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("POST", recordedRequest.method());
        assertEquals("/api/v1/sessions/jarvis-session-001/messages", recordedRequest.path());
        assertTrue(recordedRequest.header("Content-Type").startsWith("application/json"));
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertEquals("acc-1", recordedRequest.header("X-openviking-account"));
        assertEquals("user-1", recordedRequest.header("X-openviking-user"));
        assertEquals("jarvis", recordedRequest.header("X-openviking-agent"));
        assertTrue(recordedRequest.body().contains("\"role\":\"user\""));
        assertTrue(recordedRequest.body().contains("\"role_id\":\"user-1\""));
        assertTrue(recordedRequest.body().contains("\"content\":\"你好，JARVIS\""));
        assertTrue(recordedRequest.body().contains("\"created_at\":\"2026-04-30T20:00:00Z\""));
    }

    @Test
    @DisplayName("appendSessionMessage 应发送 parts 模式消息")
    void shouldAppendSessionMessageWithPartsMode() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"session_id":"jarvis-session-001","message_count":2}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingAppendSessionMessageRequest request = new OpenVikingAppendSessionMessageRequest(
                "assistant",
                "jarvis",
                null,
                List.of(
                        Map.of("type", "text", "text", "这是回答"),
                        Map.of("type", "context", "uri", "viking://resources/doc.md", "abstract", "资料摘要")
                ),
                null
        );

        OpenVikingAppendSessionMessageResponse response = client.appendSessionMessage("jarvis-session-001", request);

        assertEquals("ok", response.status());
        assertEquals("jarvis-session-001", response.result().sessionId());
        assertEquals(2, response.result().messageCount());
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("POST", recordedRequest.method());
        assertEquals("/api/v1/sessions/jarvis-session-001/messages", recordedRequest.path());
        assertTrue(recordedRequest.body().contains("\"role\":\"assistant\""));
        assertTrue(recordedRequest.body().contains("\"role_id\":\"jarvis\""));
        assertTrue(recordedRequest.body().contains("\"parts\":"));
        assertTrue(recordedRequest.body().contains("\"type\":\"text\""));
        assertTrue(recordedRequest.body().contains("\"text\":\"这是回答\""));
        assertTrue(recordedRequest.body().contains("\"type\":\"context\""));
        assertTrue(recordedRequest.body().contains("\"uri\":\"viking://resources/doc.md\""));
    }

    @Test
    @DisplayName("appendSessionMessage 遇到业务错误应抛出客户端异常")
    void shouldMapAppendSessionMessageBusinessError() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"error","error":{"code":"INVALID_ARGUMENT","message":"Either 'content' or 'parts' must be provided"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingAppendSessionMessageRequest request = new OpenVikingAppendSessionMessageRequest("user", null, null, null, null);

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.appendSessionMessage("jarvis-session-001", request)
        );

        assertEquals("OpenViking append session message failed: Either 'content' or 'parts' must be provided", exception.getMessage());
    }

    @Test
    @DisplayName("appendSessionMessage 缺少 sessionId 应直接失败")
    void shouldRejectBlankAppendSessionMessageSessionId() throws Exception {
        server = TestHttpServer.start(200, "{} ");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingAppendSessionMessageRequest request = new OpenVikingAppendSessionMessageRequest("user", null, "hello", null, null);

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.appendSessionMessage("   ", request)
        );

        assertEquals("OpenViking append session message failed: sessionId is empty.", exception.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    @DisplayName("getSessionContext 默认入口不应发送 token_budget")
    void shouldGetSessionContextWithoutTokenBudget() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"session_id":"jarvis-session-001","contexts":[{"uri":"viking://resources/doc.md","content":"上下文"}],"usage":{"tokens":128},"extra":{"nested":true}}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingSessionContextResponse response = client.getSessionContext("jarvis-session-001");

        assertEquals("ok", response.status());
        assertEquals("jarvis-session-001", response.result().get("session_id"));
        assertTrue(response.result().containsKey("contexts"));
        assertTrue(response.result().containsKey("usage"));
        assertTrue(response.result().containsKey("extra"));
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("GET", recordedRequest.method());
        assertEquals("/api/v1/sessions/jarvis-session-001/context", recordedRequest.path());
        assertEquals(null, recordedRequest.query());
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertEquals("acc-1", recordedRequest.header("X-openviking-account"));
        assertEquals("user-1", recordedRequest.header("X-openviking-user"));
        assertEquals("jarvis", recordedRequest.header("X-openviking-agent"));
    }

    @Test
    @DisplayName("getSessionContext 显式入口应发送 token_budget")
    void shouldGetSessionContextWithTokenBudget() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"session_id":"jarvis-session-001","contexts":[]}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingSessionContextResponse response = client.getSessionContext("jarvis-session-001", 4096);

        assertEquals("ok", response.status());
        assertEquals("jarvis-session-001", response.result().get("session_id"));
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("GET", recordedRequest.method());
        assertEquals("/api/v1/sessions/jarvis-session-001/context", recordedRequest.path());
        assertEquals("token_budget=4096", recordedRequest.query());
    }

    @Test
    @DisplayName("getSessionContext 遇到业务错误应抛出客户端异常")
    void shouldMapGetSessionContextBusinessError() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"error","error":{"code":"INVALID_ARGUMENT","message":"token_budget must be greater than or equal to 0"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.getSessionContext("jarvis-session-001", -1)
        );

        assertEquals("OpenViking get session context failed: token_budget must be greater than or equal to 0", exception.getMessage());
    }

    @Test
    @DisplayName("getSessionContext 缺少 sessionId 应直接失败")
    void shouldRejectBlankGetSessionContextSessionId() throws Exception {
        server = TestHttpServer.start(200, "{} ");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.getSessionContext("   ")
        );

        assertEquals("OpenViking get session context failed: sessionId is empty.", exception.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    @DisplayName("commitSession 应提交会话并解析 taskId")
    void shouldCommitSessionAndParseTaskId() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"status":"accepted","session_id":"jarvis-session-001","task_id":"task-001","archived":true}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingCommitSessionResponse response = client.commitSession("jarvis-session-001");

        assertEquals("ok", response.status());
        assertEquals("accepted", response.result().status());
        assertEquals("jarvis-session-001", response.result().sessionId());
        assertEquals("task-001", response.result().taskId());
        assertEquals(true, response.result().archived());
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("POST", recordedRequest.method());
        assertEquals("/api/v1/sessions/jarvis-session-001/commit", recordedRequest.path());
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertEquals("acc-1", recordedRequest.header("X-openviking-account"));
        assertEquals("user-1", recordedRequest.header("X-openviking-user"));
        assertEquals("jarvis", recordedRequest.header("X-openviking-agent"));
    }

    @Test
    @DisplayName("commitSession 遇到业务错误应抛出客户端异常")
    void shouldMapCommitSessionBusinessError() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"error","error":{"code":"NOT_FOUND","message":"Session jarvis-session-404 not found"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.commitSession("jarvis-session-404")
        );

        assertEquals("OpenViking commit session failed: Session jarvis-session-404 not found", exception.getMessage());
    }

    @Test
    @DisplayName("commitSession 缺少 sessionId 应直接失败")
    void shouldRejectBlankCommitSessionId() throws Exception {
        server = TestHttpServer.start(200, "{} ");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.commitSession("   ")
        );

        assertEquals("OpenViking commit session failed: sessionId is empty.", exception.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    @DisplayName("getTask 应解析 running 任务状态")
    void shouldGetRunningTask() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"task_id":"task-001","task_type":"session_commit","status":"running","resource_id":"jarvis-session-001","created_at":1777553600.0,"updated_at":1777553601.0}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingTaskResponse response = client.getTask("task-001");

        assertEquals("ok", response.status());
        assertEquals("task-001", response.result().taskId());
        assertEquals("session_commit", response.result().taskType());
        assertEquals("running", response.result().status());
        assertEquals("jarvis-session-001", response.result().resourceId());
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("GET", recordedRequest.method());
        assertEquals("/api/v1/tasks/task-001", recordedRequest.path());
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertEquals("acc-1", recordedRequest.header("X-openviking-account"));
        assertEquals("user-1", recordedRequest.header("X-openviking-user"));
        assertEquals("jarvis", recordedRequest.header("X-openviking-agent"));
    }

    @Test
    @DisplayName("getTask 应解析 completed 任务状态和扩展结果")
    void shouldGetCompletedTaskWithResult() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"task_id":"task-002","task_type":"session_commit","status":"completed","resource_id":"jarvis-session-001","created_at":1777553600.0,"updated_at":1777553610.0,"result":{"memories_extracted":{"preference":2},"extra_field":"kept"}}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingTaskResponse response = client.getTask("task-002");

        assertEquals("completed", response.result().status());
        assertEquals("task-002", response.result().taskId());
        assertTrue(response.result().result().containsKey("memories_extracted"));
        assertEquals("kept", response.result().result().get("extra_field"));
    }

    @Test
    @DisplayName("getTask 应解析 failed 任务状态")
    void shouldGetFailedTask() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"task_id":"task-003","task_type":"session_commit","status":"failed","resource_id":"jarvis-session-001","error":"LLM timeout"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingTaskResponse response = client.getTask("task-003");

        assertEquals("failed", response.result().status());
        assertEquals("LLM timeout", response.result().error());
    }

    @Test
    @DisplayName("getTask 遇到业务错误应抛出客户端异常")
    void shouldMapGetTaskBusinessError() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"error","error":{"code":"NOT_FOUND","message":"Task not found or expired"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.getTask("missing-task")
        );

        assertEquals("OpenViking get task failed: Task not found or expired", exception.getMessage());
    }

    @Test
    @DisplayName("getTask 缺少 taskId 应直接失败")
    void shouldRejectBlankTaskId() throws Exception {
        server = TestHttpServer.start(200, "{} ");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.getTask("   ")
        );

        assertEquals("OpenViking get task failed: taskId is empty.", exception.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    @DisplayName("search 不带 sessionId 时应调用 session-aware search 接口")
    void shouldSearchWithoutSessionId() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"memories":[{"uri":"viking://memories/user-profile","context_type":"memory","is_leaf":true,"abstract":"用户偏好 Java","category":"preference","score":0.91,"match_reason":"semantic"}],"resources":[],"skills":[],"total":1,"extra_field":"ignored"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingSearchRequest request = new OpenVikingSearchRequest(
                "用户偏好",
                "viking://memories",
                null,
                5,
                null,
                0.6,
                null,
                false,
                null,
                null,
                null
        );

        OpenVikingSearchResponse response = client.search(request);

        assertEquals("ok", response.status());
        assertEquals(1, response.result().total());
        assertEquals("viking://memories/user-profile", response.result().memories().getFirst().uri());
        assertEquals("memory", response.result().memories().getFirst().contextType());
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("POST", recordedRequest.method());
        assertEquals("/api/v1/search/search", recordedRequest.path());
        assertTrue(recordedRequest.header("Content-Type").startsWith("application/json"));
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertEquals("acc-1", recordedRequest.header("X-openviking-account"));
        assertEquals("user-1", recordedRequest.header("X-openviking-user"));
        assertEquals("jarvis", recordedRequest.header("X-openviking-agent"));
        assertTrue(recordedRequest.body().contains("\"query\":\"用户偏好\""));
        assertTrue(recordedRequest.body().contains("\"target_uri\":\"viking://memories\""));
        assertTrue(recordedRequest.body().contains("\"limit\":5"));
        assertTrue(recordedRequest.body().contains("\"score_threshold\":0.6"));
        assertTrue(recordedRequest.body().contains("\"include_provenance\":false"));
        assertTrue(!recordedRequest.body().contains("\"session_id\""));
        assertTrue(!recordedRequest.body().contains("\"telemetry\""));
    }

    @Test
    @DisplayName("search 带 sessionId 和过滤条件时应发送完整请求体")
    void shouldSearchWithSessionIdAndFilters() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"memories":[],"resources":[{"uri":"viking://resources/resume.md","context_type":"resource","is_leaf":true,"abstract":"简历资料","category":"profile","score":0.88,"match_reason":"session_context"}],"skills":[{"uri":"viking://skills/interview","context_type":"skill","is_leaf":true,"abstract":"面试技能","category":"skill","score":0.77,"match_reason":"semantic"}],"total":2}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingSearchRequest request = new OpenVikingSearchRequest(
                "结合会话找资料",
                List.of("viking://resources", "viking://skills"),
                "jarvis-session-001",
                10,
                20,
                0.55,
                Map.of("category", "profile", "tags", List.of("java", "ai")),
                true,
                "2026-04-01T00:00:00Z",
                "2026-04-30T23:59:59Z",
                "updated_at"
        );

        OpenVikingSearchResponse response = client.search(request);

        assertEquals("ok", response.status());
        assertEquals(2, response.result().total());
        assertEquals("viking://resources/resume.md", response.result().resources().getFirst().uri());
        assertEquals("viking://skills/interview", response.result().skills().getFirst().uri());
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("POST", recordedRequest.method());
        assertEquals("/api/v1/search/search", recordedRequest.path());
        assertTrue(recordedRequest.body().contains("\"query\":\"结合会话找资料\""));
        assertTrue(recordedRequest.body().contains("\"target_uri\":[\"viking://resources\",\"viking://skills\"]"));
        assertTrue(recordedRequest.body().contains("\"session_id\":\"jarvis-session-001\""));
        assertTrue(recordedRequest.body().contains("\"limit\":10"));
        assertTrue(recordedRequest.body().contains("\"node_limit\":20"));
        assertTrue(recordedRequest.body().contains("\"score_threshold\":0.55"));
        assertTrue(recordedRequest.body().contains("\"filter\":"));
        assertTrue(recordedRequest.body().contains("\"category\":\"profile\""));
        assertTrue(recordedRequest.body().contains("\"tags\":[\"java\",\"ai\"]"));
        assertTrue(recordedRequest.body().contains("\"include_provenance\":true"));
        assertTrue(recordedRequest.body().contains("\"since\":\"2026-04-01T00:00:00Z\""));
        assertTrue(recordedRequest.body().contains("\"until\":\"2026-04-30T23:59:59Z\""));
        assertTrue(recordedRequest.body().contains("\"time_field\":\"updated_at\""));
        assertTrue(!recordedRequest.body().contains("\"telemetry\""));
    }

    @Test
    @DisplayName("search 遇到业务错误应抛出客户端异常")
    void shouldMapSessionSearchBusinessError() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"error","error":{"code":"INVALID_ARGUMENT","message":"query is required"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingSearchRequest request = new OpenVikingSearchRequest(null, "", null, 10, null, null, null, false, null, null, null);

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.search(request)
        );

        assertEquals("OpenViking session search failed: query is required", exception.getMessage());
    }

    @Test
    @DisplayName("search 缺少请求体应直接失败")
    void shouldRejectNullSessionSearchRequest() throws Exception {
        server = TestHttpServer.start(200, "{} ");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.search(null)
        );

        assertEquals("OpenViking session search failed: request is null.", exception.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    @DisplayName("addSkill 应发送 data 请求体")
    void shouldAddSkillWithData() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"root_uri":"viking://agent/skills/search-web/","name":"search-web"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingSkillAddRequest request = new OpenVikingSkillAddRequest(
                Map.of("name", "search-web", "description", "Search web", "content", "# search-web"),
                null,
                false,
                null
        );

        OpenVikingSkillAddResponse response = client.addSkill(request);

        assertEquals("ok", response.status());
        assertEquals("viking://agent/skills/search-web/", response.result().get("root_uri"));
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("POST", recordedRequest.method());
        assertEquals("/api/v1/skills", recordedRequest.path());
        assertTrue(recordedRequest.header("Content-Type").startsWith("application/json"));
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertTrue(recordedRequest.body().contains("\"data\":{\"description\":\"Search web\",\"content\":\"# search-web\",\"name\":\"search-web\"}")
                || recordedRequest.body().contains("\"data\":{\"name\":\"search-web\""));
        assertTrue(recordedRequest.body().contains("\"wait\":false"));
        assertTrue(!recordedRequest.body().contains("temp_file_id"));
    }

    @Test
    @DisplayName("addSkill 遇到业务错误应抛出客户端异常")
    void shouldMapAddSkillBusinessError() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"error","error":{"code":"CONFLICT","message":"Skill already exists"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());
        OpenVikingSkillAddRequest request = new OpenVikingSkillAddRequest("---\nname: demo\n---", null, false, null);

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.addSkill(request)
        );

        assertEquals("OpenViking add skill failed: Skill already exists", exception.getMessage());
    }

    @Test
    @DisplayName("tempUpload 应发送 multipart 文件")
    void shouldTempUploadMultipartFile() throws Exception {
        server = TestHttpServer.start(200, """
                {"status":"ok","result":{"temp_file_id":"upload_abc123.md"}}
                """);
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingTempUploadResponse response = client.tempUpload("SKILL.md", "# Demo".getBytes(StandardCharsets.UTF_8), "text/markdown");

        assertEquals("ok", response.status());
        assertEquals("upload_abc123.md", response.result().tempFileId());
        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("POST", recordedRequest.method());
        assertEquals("/api/v1/resources/temp_upload", recordedRequest.path());
        assertTrue(recordedRequest.header("Content-Type").startsWith("multipart/form-data"));
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertTrue(recordedRequest.body().contains("name=\"file\""));
        assertTrue(recordedRequest.body().contains("filename=\"SKILL.md\""));
        assertTrue(recordedRequest.body().contains("# Demo"));
    }

    @Test
    @DisplayName("remove 应发送删除资源请求")
    void shouldRemoveResourceWithExpectedHttpContract() throws Exception {
        server = TestHttpServer.start(204, "");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        client.remove("viking://user/u-1/memories/feedback.md", false);

        TestHttpServer.RecordedRequest recordedRequest = server.singleRequest();
        assertEquals("DELETE", recordedRequest.method());
        assertEquals("/api/v1/fs", recordedRequest.path());
        String decodedQuery = java.net.URLDecoder.decode(recordedRequest.query(), StandardCharsets.UTF_8);
        assertTrue(decodedQuery.contains("uri=viking://user/u-1/memories/feedback.md"));
        assertTrue(decodedQuery.contains("recursive=false"));
        assertEquals("test-key", recordedRequest.header("X-api-key"));
        assertEquals("acc-1", recordedRequest.header("X-openviking-account"));
        assertEquals("user-1", recordedRequest.header("X-openviking-user"));
        assertEquals("jarvis", recordedRequest.header("X-openviking-agent"));
    }

    @Test
    @DisplayName("remove 缺少 uri 应直接失败")
    void shouldRejectBlankRemoveUri() throws Exception {
        server = TestHttpServer.start(204, "");
        OpenVikingClient client = new OpenVikingClient(createProperties(server.baseUrl()), new ObjectMapper());

        OpenVikingClientException exception = assertThrows(
                OpenVikingClientException.class,
                () -> client.remove("   ", false)
        );

        assertEquals("OpenViking remove failed: uri is empty.", exception.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    private static OpenVikingProperties createProperties(String baseUrl) {
        OpenVikingProperties properties = new OpenVikingProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setAccount("acc-1");
        properties.setUser("user-1");
        properties.setAgent("jarvis");
        properties.setTimeout(Duration.ofSeconds(5));
        return properties;
    }

    private static final class TestHttpServer {
        private final HttpServer server;
        private final ExecutorService executor;
        private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestHttpServer start(int responseStatus, String responseBody) throws IOException {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            TestHttpServer testServer = new TestHttpServer(httpServer, executor);
            httpServer.createContext("/", exchange -> testServer.handle(exchange, responseStatus, responseBody));
            httpServer.setExecutor(executor);
            httpServer.start();
            return testServer;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<RecordedRequest> requests() {
            return requests;
        }

        RecordedRequest singleRequest() {
            assertEquals(1, requests.size());
            return requests.getFirst();
        }

        void stop() {
            server.stop(0);
            executor.shutdownNow();
        }

        private void handle(HttpExchange exchange, int responseStatus, String responseBody) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(),
                    exchange.getRequestHeaders(),
                    body
            ));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private record RecordedRequest(
                String method,
                String path,
                String query,
                com.sun.net.httpserver.Headers headers,
                String body
        ) {
            String header(String name) {
                String value = headers.getFirst(name);
                assertNotNull(value, "Missing header: " + name);
                return value;
            }
        }
    }
}
