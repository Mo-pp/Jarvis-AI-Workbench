package com.msz.resume.ai.integrations.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpResourceToolExecutorsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("listResources() 返回资源和资源模板 JSON")
    void listResources_shouldReturnResourcesAndTemplatesAsJson() throws Exception {
        TestMcpClient client = new TestMcpClient();
        ToolExecutor executor = McpResourceToolExecutors.listResources(client);

        String result = executor.execute(ToolExecutionRequest.builder()
                .id("call-1")
                .name("mcp_test_list_resources")
                .arguments("{}")
                .build(), null);

        JsonNode root = MAPPER.readTree(result);
        assertEquals("test-server", root.path("server").asText());
        assertEquals("file:///docs/readme.md", root.path("resources").get(0).path("uri").asText());
        assertEquals("file:///{path}", root.path("resourceTemplates").get(0).path("uriTemplate").asText());
    }

    @Test
    @DisplayName("readResource() 按 URI 读取文本资源并截断")
    void readResource_shouldReadTextResourceAndTruncate() throws Exception {
        TestMcpClient client = new TestMcpClient();
        McpProperties.Resources config = new McpProperties.Resources();
        config.setMaxTextChars(5);
        ToolExecutor executor = McpResourceToolExecutors.readResource(client, config);

        String result = executor.execute(ToolExecutionRequest.builder()
                .id("call-2")
                .name("mcp_test_read_resource")
                .arguments("{\"uri\":\"file:///docs/readme.md\"}")
                .build(), null);

        JsonNode root = MAPPER.readTree(result);
        assertEquals("file:///docs/readme.md", root.path("uri").asText());
        assertEquals("file:///docs/readme.md", root.path("contents").get(0).path("uri").asText());
        assertEquals("hello...", root.path("contents").get(0).path("text").asText());
        assertTrue(root.path("contents").get(0).path("truncated").asBoolean());
    }

    @Test
    @DisplayName("readResource() 缺少 URI 时抛出参数错误")
    void readResource_whenUriMissing_shouldThrow() {
        TestMcpClient client = new TestMcpClient();
        ToolExecutor executor = McpResourceToolExecutors.readResource(client, new McpProperties.Resources());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> executor.execute(ToolExecutionRequest.builder()
                        .id("call-3")
                        .name("mcp_test_read_resource")
                        .arguments("{}")
                        .build(), null));

        assertTrue(error.getMessage().contains("uri"));
    }

    private static class TestMcpClient implements McpClient {

        @Override
        public String key() {
            return "test-server";
        }

        @Override
        public List<ToolSpecification> listTools() {
            return List.of();
        }

        @Override
        public List<ToolSpecification> listTools(InvocationContext invocationContext) {
            return listTools();
        }

        @Override
        public ToolExecutionResult executeTool(ToolExecutionRequest toolExecutionRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ToolExecutionResult executeTool(ToolExecutionRequest toolExecutionRequest, InvocationContext invocationContext) {
            return executeTool(toolExecutionRequest);
        }

        @Override
        public List<McpResource> listResources() {
            return List.of(new McpResource(
                    "file:///docs/readme.md",
                    "README",
                    "Project readme",
                    "text/markdown"
            ));
        }

        @Override
        public List<McpResource> listResources(InvocationContext invocationContext) {
            return listResources();
        }

        @Override
        public List<McpResourceTemplate> listResourceTemplates() {
            return List.of(new McpResourceTemplate(
                    "file:///{path}",
                    "File by path",
                    "Read file by path",
                    "text/plain"
            ));
        }

        @Override
        public List<McpResourceTemplate> listResourceTemplates(InvocationContext invocationContext) {
            return listResourceTemplates();
        }

        @Override
        public McpReadResourceResult readResource(String uri) {
            return new McpReadResourceResult(List.of(
                    new McpTextResourceContents(uri, "hello world", "text/plain")
            ));
        }

        @Override
        public McpReadResourceResult readResource(String uri, InvocationContext invocationContext) {
            return readResource(uri);
        }

        @Override
        public void subscribeToResource(String uri) {
        }

        @Override
        public void unsubscribeFromResource(String uri) {
        }

        @Override
        public List<McpPrompt> listPrompts() {
            return List.of();
        }

        @Override
        public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkHealth() {
        }

        @Override
        public void setRoots(List<McpRoot> roots) {
        }

        @Override
        public void close() {
        }
    }
}
