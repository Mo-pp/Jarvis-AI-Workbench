package com.msz.resume.ai.integrations.mcp;

import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistrar {

    private static final int MAX_DESCRIPTION_CHARS = 1200;

    private final McpProperties properties;
    private final McpClientManager clientManager;
    private final ToolRegistry toolRegistry;

    @PostConstruct
    public void registerMcpTools() {
        if (!properties.isEnabled()) {
            return;
        }

        int totalRegistered = 0;
        List<McpClient> clients = clientManager.initializeClients();
        for (McpClient client : clients) {
            McpProperties.Server server = findServer(client.key());
            if (server == null) {
                log.warn("[MCP] 找不到 server 配置，跳过工具注册: key={}", client.key());
                continue;
            }
            try {
                totalRegistered += registerClientTools(client, server);
            } catch (Exception e) {
                if (properties.isFailFast()) {
                    throw new IllegalStateException("MCP 工具注册失败: " + client.key(), e);
                }
                log.warn("[MCP] 工具注册失败，已跳过: key={}, error={}", client.key(), e.getMessage());
            }
        }

        log.info("[MCP] 工具注册完成: clients={}, registered={}", clients.size(), totalRegistered);
    }

    public Map<String, Object> refreshServer(String key) {
        McpProperties.Server server = clientManager.findServer(key)
                .orElseThrow(() -> new IllegalArgumentException("未找到 MCP server: " + key));
        String prefix = configuredPrefix(server);
        List<String> removedTools = toolRegistry.removeToolsByPrefix(prefix);
        McpClient client = clientManager.refreshClient(key);
        int registered = registerClientTools(client, server);
        return Map.of(
                "server", key,
                "removedTools", removedTools,
                "registeredTools", registered
        );
    }

    private int registerClientTools(McpClient client, McpProperties.Server server) {
        List<ToolSpecification> physicalSpecs = client.listTools();
        int count = 0;
        for (ToolSpecification physicalSpec : physicalSpecs) {
            if (!isAllowed(server, physicalSpec.name())) {
                log.debug("[MCP] 工具被 allow/deny 规则过滤: client={}, tool={}", client.key(), physicalSpec.name());
                continue;
            }

            String logicalName = McpToolNameMapper.logicalName(server, physicalSpec);
            ToolSpecification logicalSpec = physicalSpec.toBuilder()
                    .name(logicalName)
                    .description(description(client, physicalSpec))
                    .build();
            ToolRegistry.ToolExposure exposure = server.getExposure() == McpProperties.Exposure.CORE
                    ? ToolRegistry.ToolExposure.CORE
                    : ToolRegistry.ToolExposure.DEFERRED;

            toolRegistry.registerTool(
                    logicalName,
                    logicalSpec,
                    new McpToolExecutorAdapter(client, logicalName, physicalSpec.name()),
                    exposure,
                    client
            );
            count++;
        }
        count += registerResourceTools(client, server);
        log.info("[MCP] Client 工具注册完成: key={}, listed={}, registered={}",
                client.key(), physicalSpecs.size(), count);
        return count;
    }

    private int registerResourceTools(McpClient client, McpProperties.Server server) {
        McpProperties.Resources resources = server.getResources();
        if (resources == null || !resources.isEnabled()) {
            return 0;
        }

        ToolRegistry.ToolExposure exposure = resources.getExposure() == McpProperties.Exposure.CORE
                ? ToolRegistry.ToolExposure.CORE
                : ToolRegistry.ToolExposure.DEFERRED;

        ToolSpecification listResourcesSpec = McpResourceToolSpecifications.listResources(server);
        toolRegistry.registerTool(
                listResourcesSpec.name(),
                listResourcesSpec,
                McpResourceToolExecutors.listResources(client),
                exposure,
                client
        );

        ToolSpecification readResourceSpec = McpResourceToolSpecifications.readResource(server);
        toolRegistry.registerTool(
                readResourceSpec.name(),
                readResourceSpec,
                McpResourceToolExecutors.readResource(client, resources),
                exposure,
                client
        );

        log.info("[MCP] Resource 工具注册完成: key={}, tools=[{}, {}], exposure={}",
                client.key(), listResourcesSpec.name(), readResourceSpec.name(), exposure);
        return 2;
    }

    private boolean isAllowed(McpProperties.Server server, String toolName) {
        Set<String> denyTools = normalized(server.getDenyTools());
        if (denyTools.contains(normalize(toolName))) {
            return false;
        }

        Set<String> allowTools = normalized(server.getAllowTools());
        return allowTools.isEmpty() || allowTools.contains(normalize(toolName));
    }

    private Set<String> normalized(List<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(normalize(value));
            }
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String description(McpClient client, ToolSpecification spec) {
        String description = spec.description() == null ? "" : spec.description().trim();
        if (description.length() > MAX_DESCRIPTION_CHARS) {
            description = description.substring(0, MAX_DESCRIPTION_CHARS) + "...";
        }
        return "[MCP:" + client.key() + "] " + description;
    }

    private McpProperties.Server findServer(String key) {
        return properties.getServers().stream()
                .filter(server -> key.equals(server.getKey()))
                .findFirst()
                .orElse(null);
    }

    private String configuredPrefix(McpProperties.Server server) {
        String prefix = server.getToolPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix;
        }
        return "mcp_" + McpToolNameMapper.sanitize(server.getKey()) + "_";
    }
}
