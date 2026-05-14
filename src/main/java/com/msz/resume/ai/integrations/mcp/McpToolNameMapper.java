package com.msz.resume.ai.integrations.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Locale;

final class McpToolNameMapper {

    private McpToolNameMapper() {
    }

    static String logicalName(McpProperties.Server server, ToolSpecification specification) {
        String prefix = server.getToolPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "mcp_" + sanitize(server.getKey()) + "_";
        }
        return prefix + sanitize(specification.name());
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "tool";
        }
        String sanitized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "tool" : sanitized;
    }
}
