package com.msz.resume.ai.integrations.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpResourceToolExecutors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpResourceToolExecutors() {
    }

    static ToolExecutor listResources(McpClient client) {
        return (request, memoryId) -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("server", client.key());
            payload.put("resources", client.listResources().stream()
                    .map(McpResourceToolExecutors::resourceDescription)
                    .toList());
            payload.put("resourceTemplates", client.listResourceTemplates().stream()
                    .map(McpResourceToolExecutors::templateDescription)
                    .toList());
            return toJson(payload);
        };
    }

    static ToolExecutor readResource(McpClient client, McpProperties.Resources config) {
        return (request, memoryId) -> {
            JsonNode root = parseArguments(request);
            String uri = requiredText(root, "uri");
            McpReadResourceResult result = client.readResource(uri);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("server", client.key());
            payload.put("uri", uri);
            List<Map<String, Object>> contents = new ArrayList<>();
            for (McpResourceContents content : result.contents()) {
                contents.add(contentDescription(content, config));
            }
            payload.put("contents", contents);
            return toJson(payload);
        };
    }

    private static Map<String, Object> resourceDescription(McpResource resource) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("uri", resource.uri());
        item.put("name", resource.name());
        item.put("description", resource.description());
        item.put("mimeType", resource.mimeType());
        return item;
    }

    private static Map<String, Object> templateDescription(McpResourceTemplate template) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("uriTemplate", template.uriTemplate());
        item.put("name", template.name());
        item.put("description", template.description());
        item.put("mimeType", template.mimeType());
        return item;
    }

    private static Map<String, Object> contentDescription(McpResourceContents content, McpProperties.Resources config) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", content.type().name());
        if (content instanceof McpTextResourceContents textContent) {
            item.put("uri", textContent.uri());
            item.put("mimeType", textContent.mimeType());
            item.put("text", truncate(textContent.text(), config.getMaxTextChars()));
            item.put("truncated", textContent.text() != null && textContent.text().length() > config.getMaxTextChars());
            return item;
        }
        if (content instanceof McpBlobResourceContents blobContent) {
            item.put("uri", blobContent.uri());
            item.put("mimeType", blobContent.mimeType());
            item.put("blobPreview", truncate(blobContent.blob(), config.getMaxBlobChars()));
            item.put("truncated", blobContent.blob() != null && blobContent.blob().length() > config.getMaxBlobChars());
            return item;
        }
        item.put("value", content.toString());
        return item;
    }

    private static JsonNode parseArguments(ToolExecutionRequest request) {
        try {
            return MAPPER.readTree(request.arguments());
        } catch (Exception e) {
            throw new IllegalArgumentException("MCP resource 工具参数不是合法 JSON: " + e.getMessage(), e);
        }
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("缺少必填参数: " + field);
        }
        return node.asText();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || maxChars < 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private static String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 MCP resource 结果失败", e);
        }
    }
}
