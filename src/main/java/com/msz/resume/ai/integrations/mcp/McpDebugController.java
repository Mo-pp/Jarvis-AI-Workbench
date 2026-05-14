package com.msz.resume.ai.integrations.mcp;

import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/debug/mcp")
@RequiredArgsConstructor
public class McpDebugController {

    private final McpProperties properties;
    private final McpClientManager clientManager;
    private final McpToolRegistrar toolRegistrar;
    private final ToolRegistry toolRegistry;

    @GetMapping("/servers")
    public Map<String, Object> servers() {
        List<Map<String, Object>> servers = properties.getServers().stream()
                .map(this::serverSummary)
                .toList();
        return Map.of(
                "enabled", properties.isEnabled(),
                "failFast", properties.isFailFast(),
                "servers", servers
        );
    }

    @GetMapping("/servers/{key}/tools")
    public Map<String, Object> tools(@PathVariable String key) {
        McpClient client = requireClient(key);
        McpProperties.Server server = requireServer(key);
        List<Map<String, Object>> tools = client.listTools().stream()
                .map(spec -> toolSummary(server, spec))
                .toList();
        return Map.of(
                "server", key,
                "count", tools.size(),
                "tools", tools
        );
    }

    @GetMapping("/servers/{key}/resources")
    public Map<String, Object> resources(@PathVariable String key) {
        McpClient client = requireClient(key);
        List<Map<String, Object>> resources = client.listResources().stream()
                .map(this::resourceSummary)
                .toList();
        List<Map<String, Object>> templates = client.listResourceTemplates().stream()
                .map(this::resourceTemplateSummary)
                .toList();
        return Map.of(
                "server", key,
                "resources", resources,
                "resourceTemplates", templates
        );
    }

    @PostMapping("/servers/{key}/health")
    public Map<String, Object> health(@PathVariable String key) {
        McpClient client = requireClient(key);
        try {
            client.checkHealth();
            return Map.of(
                    "server", key,
                    "healthy", true
            );
        } catch (Exception e) {
            log.warn("[MCP Debug] health check failed: key={}, error={}", key, e.getMessage());
            return Map.of(
                    "server", key,
                    "healthy", false,
                    "error", e.getMessage()
            );
        }
    }

    @PostMapping("/servers/{key}/refresh")
    public Map<String, Object> refresh(@PathVariable String key) {
        log.info("[MCP Debug] refresh server: key={}", key);
        try {
            Map<String, Object> result = toolRegistrar.refreshServer(key);
            return Map.of(
                    "success", true,
                    "result", result
            );
        } catch (Exception e) {
            log.warn("[MCP Debug] refresh failed: key={}, error={}", key, e.getMessage());
            return Map.of(
                    "success", false,
                    "server", key,
                    "error", e.getMessage()
            );
        }
    }

    private Map<String, Object> serverSummary(McpProperties.Server server) {
        String key = server.getKey();
        McpClient client = key == null ? null : clientManager.getClient(key);
        String prefix = toolPrefix(server);
        long registeredTools = toolRegistry.getAllToolNames().stream()
                .filter(name -> name.startsWith(prefix))
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key", key);
        summary.put("enabled", server.isEnabled());
        summary.put("connected", client != null);
        summary.put("transport", server.getTransport());
        summary.put("url", server.getUrl());
        summary.put("toolPrefix", prefix);
        summary.put("exposure", server.getExposure());
        summary.put("registeredTools", registeredTools);
        summary.put("resourcesEnabled", server.getResources() != null && server.getResources().isEnabled());
        return summary;
    }

    private Map<String, Object> toolSummary(McpProperties.Server server, ToolSpecification spec) {
        Map<String, Object> item = new LinkedHashMap<>();
        String logicalName = McpToolNameMapper.logicalName(server, spec);
        item.put("physicalName", spec.name());
        item.put("logicalName", logicalName);
        item.put("registered", toolRegistry.hasTool(logicalName));
        item.put("description", spec.description());
        item.put("parameters", spec.parameters());
        return item;
    }

    private Map<String, Object> resourceSummary(McpResource resource) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("uri", resource.uri());
        item.put("name", resource.name());
        item.put("description", resource.description());
        item.put("mimeType", resource.mimeType());
        return item;
    }

    private Map<String, Object> resourceTemplateSummary(McpResourceTemplate template) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("uriTemplate", template.uriTemplate());
        item.put("name", template.name());
        item.put("description", template.description());
        item.put("mimeType", template.mimeType());
        return item;
    }

    private McpClient requireClient(String key) {
        McpClient client = clientManager.getClient(key);
        if (client == null) {
            throw new IllegalArgumentException("MCP client 未初始化: " + key);
        }
        return client;
    }

    private McpProperties.Server requireServer(String key) {
        return clientManager.findServer(key)
                .orElseThrow(() -> new IllegalArgumentException("未找到 MCP server: " + key));
    }

    private String toolPrefix(McpProperties.Server server) {
        String prefix = server.getToolPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix;
        }
        return "mcp_" + McpToolNameMapper.sanitize(server.getKey()) + "_";
    }
}
