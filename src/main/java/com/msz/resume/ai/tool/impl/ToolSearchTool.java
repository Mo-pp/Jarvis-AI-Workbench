package com.msz.resume.ai.tool.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.tool.CoreTool;
import com.msz.resume.ai.tool.registry.ToolHint;
import com.msz.resume.ai.tool.registry.ToolRegistry;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具搜索工具（核心工具）
 *
 * 用于搜索并加载延迟工具的完整 schema。
 * LLM 通过此工具发现可用的延迟工具。
 */
@Slf4j
@CoreTool
@Component
@RequiredArgsConstructor
public class ToolSearchTool {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 搜索并加载延迟工具的完整 schema
     *
     * @param toolName 工具名称或关键词
     * @return 工具 schema JSON 或搜索结果
     */
    @Tool("搜索并加载延迟工具的完整 schema。输入工具名称或关键词，返回工具的详细参数信息。")
    public String toolSearch(String toolName) {
        log.info("[工具调用] toolSearch, query={}", toolName);

        if (toolName == null || toolName.isBlank()) {
            return buildAvailableToolsResponse();
        }

        // 1. 精确匹配
        ToolSpecification exactMatch = toolRegistry.getDeferredToolSpecification(toolName);
        if (exactMatch != null) {
            log.info("[工具搜索] 精确匹配: {}", toolName);
            return buildToolSchemaJson(exactMatch);
        }

        // 2. 模糊搜索
        List<ToolHint> matches = toolRegistry.searchDeferredTools(toolName);
        if (!matches.isEmpty()) {
            log.info("[工具搜索] 模糊匹配到 {} 个工具", matches.size());
            return buildSearchResultResponse(toolName, matches);
        }

        // 3. 无匹配
        log.info("[工具搜索] 未找到匹配工具: {}", toolName);
        return buildNotFoundResponse(toolName);
    }

    /**
     * 构建工具 schema JSON
     */
    private String buildToolSchemaJson(ToolSpecification spec) {
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("name", spec.name());
            schema.put("description", spec.description() != null ? spec.description() : "");
            schema.put("parameters", buildParametersSchema(spec));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            log.error("[工具搜索] JSON 序列化失败", e);
            return "{\"error\": \"JSON 序列化失败\"}";
        }
    }

    /**
     * 构建参数 schema（转换为可序列化的 Map）
     */
    private Map<String, Object> buildParametersSchema(ToolSpecification spec) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        if (spec.parameters() != null) {
            JsonObjectSchema jsonSchema = spec.parameters();

            // 转换 properties
            Map<String, Object> properties = new LinkedHashMap<>();
            if (jsonSchema.properties() != null) {
                for (Map.Entry<String, JsonSchemaElement> entry : jsonSchema.properties().entrySet()) {
                    properties.put(entry.getKey(), convertJsonSchemaElement(entry.getValue()));
                }
            }
            params.put("properties", properties);

            // 转换 required
            if (jsonSchema.required() != null && !jsonSchema.required().isEmpty()) {
                params.put("required", jsonSchema.required());
            }
        } else {
            params.put("properties", Map.of());
        }

        return params;
    }

    /**
     * 递归转换 JsonSchemaElement 为可序列化的 Map
     */
    private Map<String, Object> convertJsonSchemaElement(JsonSchemaElement element) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 根据 element 类型转换
        String typeStr = element.getClass().getSimpleName().replace("Json", "").toLowerCase();
        if (typeStr.contains("string")) {
            result.put("type", "string");
        } else if (typeStr.contains("integer")) {
            result.put("type", "integer");
        } else if (typeStr.contains("number")) {
            result.put("type", "number");
        } else if (typeStr.contains("boolean")) {
            result.put("type", "boolean");
        } else if (typeStr.contains("array")) {
            result.put("type", "array");
        } else if (typeStr.contains("object")) {
            result.put("type", "object");
        } else {
            result.put("type", "string"); // 默认
        }

        // 尝试获取 description
        try {
            var descriptionMethod = element.getClass().getMethod("description");
            Object desc = descriptionMethod.invoke(element);
            if (desc != null) {
                result.put("description", desc.toString());
            }
        } catch (Exception ignored) {
            // 没有 description 字段
        }

        return result;
    }

    /**
     * 构建搜索结果响应
     */
    private String buildSearchResultResponse(String query, List<ToolHint> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到以下工具匹配 \"").append(query).append("\":\n\n");

        for (ToolHint hint : matches) {
            sb.append("- ").append(hint.name());
            if (hint.description() != null && !hint.description().isEmpty()) {
                sb.append(": ").append(hint.description());
            }
            sb.append("\n");
        }

        sb.append("\n使用精确工具名再次调用 toolSearch 获取完整 schema。");
        return sb.toString();
    }

    /**
     * 构建无匹配响应
     */
    private String buildNotFoundResponse(String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("未找到匹配 \"").append(query).append("\" 的工具。\n\n");
        sb.append(buildAvailableToolsResponse());
        return sb.toString();
    }

    /**
     * 构建可用工具列表响应
     */
    private String buildAvailableToolsResponse() {
        List<ToolHint> hints = toolRegistry.getDeferredToolHints();

        if (hints.isEmpty()) {
            return "当前没有可用的延迟工具。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("可用的延迟工具:\n\n");

        for (ToolHint hint : hints) {
            sb.append("- ").append(hint.name());
            if (hint.description() != null && !hint.description().isEmpty()) {
                sb.append(": ").append(hint.description());
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
