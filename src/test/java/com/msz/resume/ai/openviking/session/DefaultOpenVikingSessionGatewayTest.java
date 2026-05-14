package com.msz.resume.ai.integrations.openviking.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultOpenVikingSessionGateway 单元测试。
 *
 * <p>测试覆盖：</p>
 * <ul>
 *     <li>会话创建成功和幂等性</li>
 *     <li>消息追加成功和失败</li>
 *     <li>上下文加载成功、为空、失败</li>
 *     <li>提交成功和失败</li>
 *     <li>配置开关控制</li>
 *     <li>降级行为：失败不抛异常</li>
 * </ul>
 */
class DefaultOpenVikingSessionGatewayTest {

    private TestHttpServer server;
    private OpenVikingClient client;
    private OpenVikingSessionProperties sessionProperties;
    private OpenVikingSessionContextFormatter contextFormatter;

    @BeforeEach
    void setUp() throws IOException {
        server = TestHttpServer.start(200, "{}");
        OpenVikingProperties properties = createBaseProperties(server.baseUrl());
        client = new OpenVikingClient(properties, new ObjectMapper());
        sessionProperties = new OpenVikingSessionProperties();
        sessionProperties.setEnabled(true);
        contextFormatter = new OpenVikingSessionContextFormatter();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    // ==================== ensureSession Tests ====================

    @Nested
    @DisplayName("ensureSession 测试")
    class EnsureSessionTests {

        @Test
        @DisplayName("新会话创建成功应返回 true")
        void shouldReturnTrueWhenCreateSuccess() throws IOException {
            server.setResponse(200, """
                    {"status":"ok","result":{"session_id":"session-001"}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.ensureSession("session-001");

            assertTrue(result);
            assertEquals(1, server.requests().size());
        }

        @Test
        @DisplayName("已存在会话应返回 true（幂等性）")
        void shouldReturnTrueWhenAlreadyExists() throws IOException {
            server.setResponse(200, """
                    {"status":"error","error":{"code":"ALREADY_EXISTS","message":"session already exists"}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.ensureSession("session-001");

            assertTrue(result);
        }

        @Test
        @DisplayName("网络失败应返回 false 而不抛异常")
        void shouldReturnFalseOnNetworkError() throws IOException {
            server.stop();
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.ensureSession("session-001");

            assertFalse(result);
        }

        @Test
        @DisplayName("业务错误应返回 false")
        void shouldReturnFalseOnBusinessError() throws IOException {
            server.setResponse(200, """
                    {"status":"error","error":{"code":"INVALID_ARGUMENT","message":"invalid session_id"}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.ensureSession("session-001");

            assertFalse(result);
        }

        @Test
        @DisplayName("配置禁用时应返回 false 且不发送请求")
        void shouldSkipWhenDisabled() throws IOException {
            sessionProperties.setEnabled(false);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.ensureSession("session-001");

            assertFalse(result);
            assertEquals(0, server.requests().size());
        }

        @Test
        @DisplayName("sessionId 为空应返回 false")
        void shouldReturnFalseWhenSessionIdBlank() throws IOException {
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.ensureSession("   ");

            assertFalse(result);
        }
    }

    // ==================== appendUserMessage Tests ====================

    @Nested
    @DisplayName("appendUserMessage 测试")
    class AppendUserMessageTests {

        @Test
        @DisplayName("追加用户消息成功应返回 true")
        void shouldReturnTrueOnSuccess() throws IOException {
            server.setResponse(200, """
                    {"status":"ok","result":{"session_id":"session-001","message_count":1}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.appendUserMessage("session-001", "Hello");

            assertTrue(result);
            assertEquals(1, server.requests().size());
            String body = server.singleRequest().body();
            assertTrue(body.contains("\"role\":\"user\""));
            assertTrue(body.contains("\"content\":\"Hello\""));
        }

        @Test
        @DisplayName("配置禁用 appendUser 时应跳过")
        void shouldSkipWhenAppendUserDisabled() throws IOException {
            sessionProperties.setAppendUser(false);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.appendUserMessage("session-001", "Hello");

            assertFalse(result);
            assertEquals(0, server.requests().size());
        }

        @Test
        @DisplayName("主开关禁用时应跳过")
        void shouldSkipWhenMainDisabled() throws IOException {
            sessionProperties.setEnabled(false);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.appendUserMessage("session-001", "Hello");

            assertFalse(result);
            assertEquals(0, server.requests().size());
        }

        @Test
        @DisplayName("失败应返回 false 而不抛异常")
        void shouldReturnFalseOnError() throws IOException {
            server.setResponse(500, "Internal Server Error");
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.appendUserMessage("session-001", "Hello");

            assertFalse(result);
        }

        @Test
        @DisplayName("空内容应返回 true 且不发送请求")
        void shouldReturnTrueOnEmptyContent() throws IOException {
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.appendUserMessage("session-001", "   ");

            assertTrue(result);
            assertEquals(0, server.requests().size());
        }
    }

    // ==================== appendAssistantMessage Tests ====================

    @Nested
    @DisplayName("appendAssistantMessage 测试")
    class AppendAssistantMessageTests {

        @Test
        @DisplayName("追加助手消息成功应返回 true")
        void shouldReturnTrueOnSuccess() throws IOException {
            server.setResponse(200, """
                    {"status":"ok","result":{"session_id":"session-001","message_count":2}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.appendAssistantMessage("session-001", "Hi there!");

            assertTrue(result);
            assertEquals(1, server.requests().size());
            String body = server.singleRequest().body();
            assertTrue(body.contains("\"role\":\"assistant\""));
            assertTrue(body.contains("\"content\":\"Hi there!\""));
        }

        @Test
        @DisplayName("配置禁用 appendAssistant 时应跳过")
        void shouldSkipWhenAppendAssistantDisabled() throws IOException {
            sessionProperties.setAppendAssistant(false);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            boolean result = gateway.appendAssistantMessage("session-001", "Hi");

            assertFalse(result);
            assertEquals(0, server.requests().size());
        }
    }

    // ==================== loadSessionContext Tests ====================

    @Nested
    @DisplayName("loadSessionContext 测试")
    class LoadSessionContextTests {

        @Test
        @DisplayName("加载上下文成功应返回格式化的 Markdown")
        void shouldReturnFormattedContext() throws IOException {
            server.setResponse(200, """
                    {
                      "status": "ok",
                      "result": {
                        "latest_archive_overview": "用户讨论了 Java 开发",
                        "pre_archive_abstracts": ["之前的摘要"],
                        "messages": [
                          {"role": "user", "parts": [{"type": "text", "text": "你好"}]},
                          {"role": "assistant", "parts": [{"type": "text", "text": "你好！有什么可以帮助你的？"}]}
                        ],
                        "stats": {
                          "totalArchives": 1,
                          "activeTokens": 50,
                          "archiveTokens": 100
                        }
                      }
                    }
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.loadSessionContext("session-001", null);

            assertTrue(result.isPresent());
            String context = result.get();
            assertTrue(context.contains("## OpenViking Session Context"));
            // 格式化器输出 "### Current State" 而非 "### Archive Overview"
            assertTrue(context.contains("### Current State"));
            assertTrue(context.contains("用户讨论了 Java 开发"));
            assertTrue(context.contains("### Recent Active Messages"));
            assertTrue(context.contains("### Stats"));
        }

        @Test
        @DisplayName("空上下文应返回包含 Next Step 的内容")
        void shouldReturnNextStepWhenNoContent() throws IOException {
            // 格式化器即使在没有内容时也会输出 "### Next Step" section
            server.setResponse(200, """
                    {"status":"ok","result":{}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.loadSessionContext("session-001", null);

            // 格式化器现在始终返回内容（包含 Next Step section）
            assertTrue(result.isPresent());
            assertTrue(result.get().contains("### Next Step"));
        }

        @Test
        @DisplayName("配置禁用 contextOnCompact 时应跳过")
        void shouldSkipWhenContextOnCompactDisabled() throws IOException {
            sessionProperties.setContextOnCompact(false);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.loadSessionContext("session-001", null);

            assertFalse(result.isPresent());
            assertEquals(0, server.requests().size());
        }

        @Test
        @DisplayName("失败应返回 empty 而不抛异常")
        void shouldReturnEmptyOnError() throws IOException {
            server.setResponse(404, """
                    {"status":"error","error":{"code":"NOT_FOUND","message":"Session not found"}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.loadSessionContext("session-001", null);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("应传递 tokenBudget 参数")
        void shouldPassTokenBudget() throws IOException {
            server.setResponse(200, """
                    {"status":"ok","result":{}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            gateway.loadSessionContext("session-001", 8000);

            String query = server.singleRequest().query();
            assertTrue(query.contains("token_budget=8000"));
        }
    }

    // ==================== commitSession Tests ====================

    @Nested
    @DisplayName("commitSession 测试")
    class CommitSessionTests {

        @Test
        @DisplayName("提交成功应返回 task_id")
        void shouldReturnTaskIdOnSuccess() throws IOException {
            server.setResponse(200, """
                    {"status":"ok","result":{"status":"accepted","session_id":"session-001","task_id":"task-123","archived":true}}
                    """);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.commitSession("session-001");

            assertTrue(result.isPresent());
            assertEquals("task-123", result.get());
        }

        @Test
        @DisplayName("配置禁用 manualCommit 时应跳过")
        void shouldSkipWhenManualCommitDisabled() throws IOException {
            sessionProperties.setManualCommit(false);
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.commitSession("session-001");

            assertFalse(result.isPresent());
            assertEquals(0, server.requests().size());
        }

        @Test
        @DisplayName("失败应返回 empty 而不抛异常")
        void shouldReturnEmptyOnError() throws IOException {
            server.setResponse(500, "Internal Server Error");
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.commitSession("session-001");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("sessionId 为空应返回 empty")
        void shouldReturnEmptyWhenSessionIdBlank() throws IOException {
            DefaultOpenVikingSessionGateway gateway = createGateway();

            Optional<String> result = gateway.commitSession("   ");

            assertFalse(result.isPresent());
        }
    }

    // ==================== Helper Methods ====================

    private DefaultOpenVikingSessionGateway createGateway() {
        return new DefaultOpenVikingSessionGateway(client, sessionProperties, contextFormatter);
    }

    private static OpenVikingProperties createBaseProperties(String baseUrl) {
        OpenVikingProperties properties = new OpenVikingProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setAccount("test-account");
        properties.setUser("test-user");
        properties.setAgent("jarvis");
        properties.setTimeout(Duration.ofSeconds(5));
        return properties;
    }

    // ==================== Test HTTP Server ====================

    private static final class TestHttpServer {
        private final HttpServer server;
        private final ExecutorService executor;
        private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
        private volatile int responseStatus = 200;
        private volatile String responseBody = "{}";

        private TestHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestHttpServer start(int defaultStatus, String defaultBody) throws IOException {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            TestHttpServer testServer = new TestHttpServer(httpServer, executor);
            testServer.responseStatus = defaultStatus;
            testServer.responseBody = defaultBody;
            httpServer.createContext("/", exchange -> testServer.handle(exchange));
            httpServer.setExecutor(executor);
            httpServer.start();
            return testServer;
        }

        void setResponse(int status, String body) {
            this.responseStatus = status;
            this.responseBody = body;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<RecordedRequest> requests() {
            return requests;
        }

        RecordedRequest singleRequest() {
            assertEquals(1, requests.size(), "Expected exactly one request, got: " + requests.size());
            return requests.getFirst();
        }

        void stop() {
            server.stop(0);
            executor.shutdownNow();
        }

        private void handle(HttpExchange exchange) throws IOException {
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
