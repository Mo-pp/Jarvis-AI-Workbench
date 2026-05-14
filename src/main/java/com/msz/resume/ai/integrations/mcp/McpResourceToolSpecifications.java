package com.msz.resume.ai.integrations.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

final class McpResourceToolSpecifications {

    private McpResourceToolSpecifications() {
    }

    static String listResourcesToolName(McpProperties.Server server) {
        return prefix(server) + "list_resources";
    }

    static String readResourceToolName(McpProperties.Server server) {
        return prefix(server) + "read_resource";
    }

    static ToolSpecification listResources(McpProperties.Server server) {
        return ToolSpecification.builder()
                .name(listResourcesToolName(server))
                .description("[MCP:" + server.getKey() + "] List resources and resource templates exposed by this MCP server. Use this before reading a resource when you need external context rather than an action.")
                .parameters(JsonObjectSchema.builder()
                        .additionalProperties(false)
                        .build())
                .build();
    }

    static ToolSpecification readResource(McpProperties.Server server) {
        return ToolSpecification.builder()
                .name(readResourceToolName(server))
                .description("[MCP:" + server.getKey() + "] Read one MCP resource by URI. Call list_resources first unless you already know the exact URI.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("uri", "The exact resource URI returned by list_resources or matching a resource template.")
                        .required("uri")
                        .additionalProperties(false)
                        .build())
                .build();
    }

    private static String prefix(McpProperties.Server server) {
        String configured = server.getToolPrefix();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "mcp_" + McpToolNameMapper.sanitize(server.getKey()) + "_";
    }
}
