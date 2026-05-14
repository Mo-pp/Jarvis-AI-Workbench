package com.msz.resume.ai.integrations.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class McpToolExecutorAdapter implements ToolExecutor {

    private final McpClient client;
    private final String logicalName;
    private final String physicalName;

    McpToolExecutorAdapter(McpClient client, String logicalName, String physicalName) {
        this.client = client;
        this.logicalName = logicalName;
        this.physicalName = physicalName;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        ToolExecutionRequest physicalRequest = request.toBuilder()
                .name(physicalName)
                .build();
        ToolExecutionResult result = client.executeTool(physicalRequest);
        if (result.isError()) {
            log.warn("[MCP] 工具执行返回错误: client={}, logicalTool={}, physicalTool={}",
                    client.key(), logicalName, physicalName);
        }
        if (result.resultText() != null) {
            return result.resultText();
        }
        return result.result() == null ? "" : result.result().toString();
    }
}
