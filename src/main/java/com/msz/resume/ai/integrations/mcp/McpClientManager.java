package com.msz.resume.ai.integrations.mcp;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private final McpProperties properties;
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    public List<McpClient> initializeClients() {
        if (!properties.isEnabled()) {
            log.info("[MCP] 未启用，跳过 MCP Client 初始化");
            return List.of();
        }

        List<McpClient> initialized = new ArrayList<>();
        for (McpProperties.Server server : properties.getServers()) {
            if (!server.isEnabled()) {
                continue;
            }
            try {
                McpClient client = createClient(server);
                clients.put(client.key(), client);
                initialized.add(client);
                log.info("[MCP] Client 初始化完成: key={}, transport={}", client.key(), server.getTransport());
            } catch (Exception e) {
                String key = server.getKey() == null ? "<unknown>" : server.getKey();
                if (properties.isFailFast()) {
                    throw new IllegalStateException("MCP Client 初始化失败: " + key, e);
                }
                log.warn("[MCP] Client 初始化失败，已跳过: key={}, error={}", key, e.getMessage());
            }
        }
        return Collections.unmodifiableList(initialized);
    }

    public McpClient getClient(String key) {
        return clients.get(key);
    }

    public List<McpClient> getClients() {
        return List.copyOf(clients.values());
    }

    public Optional<McpProperties.Server> findServer(String key) {
        return properties.getServers().stream()
                .filter(server -> key.equals(server.getKey()))
                .findFirst();
    }

    public McpClient refreshClient(String key) {
        McpProperties.Server server = findServer(key)
                .orElseThrow(() -> new IllegalArgumentException("未找到 MCP server: " + key));
        if (!server.isEnabled()) {
            throw new IllegalStateException("MCP server 未启用: " + key);
        }

        McpClient oldClient = clients.remove(key);
        if (oldClient != null) {
            closeClient(oldClient);
        }

        McpClient client = createClient(server);
        clients.put(client.key(), client);
        log.info("[MCP] Client 刷新完成: key={}, transport={}", client.key(), server.getTransport());
        return client;
    }

    private McpClient createClient(McpProperties.Server server) {
        validateServer(server);
        McpTransport transport = createTransport(server);
        return DefaultMcpClient.builder()
                .key(server.getKey())
                .clientName("jarvis")
                .clientVersion("0.0.1")
                .transport(transport)
                .initializationTimeout(server.getInitializationTimeout())
                .toolExecutionTimeout(server.getToolExecutionTimeout())
                .cacheToolList(server.isCacheToolList())
                .build();
    }

    private McpTransport createTransport(McpProperties.Server server) {
        return switch (server.getTransport()) {
            case SSE_HTTP -> HttpMcpTransport.builder()
                    .sseUrl(server.getUrl())
                    .customHeaders(server.getHeaders())
                    .timeout(server.getTimeout())
                    .logRequests(server.isLogRequests())
                    .logResponses(server.isLogResponses())
                    .build();
            case STREAMABLE_HTTP -> StreamableHttpMcpTransport.builder()
                    .url(server.getUrl())
                    .customHeaders(server.getHeaders())
                    .timeout(server.getTimeout())
                    .logRequests(server.isLogRequests())
                    .logResponses(server.isLogResponses())
                    .build();
            case STDIO -> StdioMcpTransport.builder()
                    .command(server.getCommand())
                    .environment(server.getEnvironment())
                    .logEvents(server.isLogRequests() || server.isLogResponses())
                    .build();
        };
    }

    private void validateServer(McpProperties.Server server) {
        if (server.getKey() == null || server.getKey().isBlank()) {
            throw new IllegalArgumentException("MCP server key 不能为空");
        }
        if (server.getTransport() == McpProperties.Transport.STDIO) {
            if (server.getCommand() == null || server.getCommand().isEmpty()) {
                throw new IllegalArgumentException("MCP stdio server command 不能为空: " + server.getKey());
            }
            return;
        }
        if (server.getUrl() == null || server.getUrl().isBlank()) {
            throw new IllegalArgumentException("MCP HTTP server url 不能为空: " + server.getKey());
        }
    }

    @PreDestroy
    public void close() {
        for (McpClient client : clients.values()) {
            closeClient(client);
        }
    }

    private void closeClient(McpClient client) {
        try {
            client.close();
            log.info("[MCP] Client 已关闭: key={}", client.key());
        } catch (Exception e) {
            log.warn("[MCP] Client 关闭失败: key={}, error={}", client.key(), e.getMessage());
        }
    }
}
