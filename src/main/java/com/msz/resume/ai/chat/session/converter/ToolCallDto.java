package com.msz.resume.ai.chat.session.converter;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * 工具调用请求 DTO
 * 用于 JSON 序列化/反序列化 ToolExecutionRequest
 *
 * 为什么需要这个 DTO：
 * - LangChain4j 的 ToolExecutionRequest 是 record，没有无参构造函数
 * - Jackson 可以序列化 record，但无法反序列化
 * - 使用 DTO 作为中间层解决双向转换问题
 */
public record ToolCallDto(
        String id,
        String name,
        String arguments
) {
    /**
     * 从 LangChain4j ToolExecutionRequest 转换
     */
    public static ToolCallDto from(ToolExecutionRequest request) {
        return new ToolCallDto(
                request.id(),
                request.name(),
                request.arguments()
        );
    }

    /**
     * 转换为 LangChain4j ToolExecutionRequest
     */
    public ToolExecutionRequest toToolExecutionRequest() {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }
}
