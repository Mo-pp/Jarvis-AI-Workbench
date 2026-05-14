package com.msz.resume.ai.chat.runtime.trace;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publishes sparse user-visible checkpoints in the chat timeline.
 */
@Service
public class AssistantCheckpointService {

    private static final Set<String> SEARCH_TOOLS = Set.of(
            "openviking_glob",
            "openviking_grep",
            "openviking_find",
            "openviking_search"
    );

    private final TimelineActionService timelineActionService;

    public AssistantCheckpointService(TimelineActionService timelineActionService) {
        this.timelineActionService = timelineActionService;
    }

    public void toolPlan(ChatRunTraceContext traceContext,
                         TraceAgentDescriptor agentDescriptor,
                         List<ChatMessage> existingMessages,
                         List<ToolExecutionRequest> requests,
                         int turnCount) {
        if (!shouldPublish(traceContext, requests)) {
            return;
        }

        boolean strategyShift = hasRecentInsufficientToolResult(existingMessages);
        if (!strategyShift && turnCount > 1) {
            return;
        }

        ToolExecutionRequest first = requests.getFirst();
        String title = strategyShift ? "调整策略" : "开始处理";
        String content = strategyShift
                ? strategyShiftContent(first)
                : firstStepContent(first, requests.size());

        Map<String, Object> payload = timelineActionService
                .builder(checkpointId(traceContext, agentDescriptor, turnCount, strategyShift), traceContext, agentDescriptor)
                .title(title)
                .status("info")
                .put("content", content)
                .put("phase", strategyShift ? "strategy_shift" : "start")
                .build();

        publish(traceContext, payload);
    }

    public void taskPlanCreated(ChatRunTraceContext traceContext,
                                TraceAgentDescriptor agentDescriptor,
                                int taskCount) {
        if (traceContext == null || !traceContext.isActive() || taskCount <= 0) {
            return;
        }

        Map<String, Object> payload = timelineActionService
                .builder("checkpoint_" + traceContext.runId() + "_task_plan", traceContext, agentDescriptor)
                .title("已拆分任务")
                .status("info")
                .put("content", "我把这轮工作拆成 " + taskCount + " 个执行步骤，接下来按顺序推进。")
                .put("phase", "task_phase")
                .build();
        publish(traceContext, payload);
    }

    private boolean shouldPublish(ChatRunTraceContext traceContext, List<ToolExecutionRequest> requests) {
        return traceContext != null
                && traceContext.isActive()
                && requests != null
                && !requests.isEmpty();
    }

    private String checkpointId(ChatRunTraceContext traceContext,
                                TraceAgentDescriptor agentDescriptor,
                                int turnCount,
                                boolean strategyShift) {
        String agentId = agentDescriptor != null && agentDescriptor.agentId() != null
                ? agentDescriptor.agentId()
                : "main";
        return "checkpoint_" + traceContext.runId() + "_" + agentId + "_turn_" + turnCount
                + (strategyShift ? "_strategy" : "_start");
    }

    private String firstStepContent(ToolExecutionRequest first, int requestCount) {
        String suffix = requestCount > 1 ? "，并行拿几组信号。" : "。";
        return switch (first.name()) {
            case "openviking_list" -> "我先列出相关目录，看清可用范围" + suffix;
            case "openviking_tree" -> "我先浏览目录树，确定接下来该读哪些资源" + suffix;
            case "openviking_read" -> "我先读取已定位的资源，再基于内容继续判断" + suffix;
            case "openviking_glob" -> "我先按路径模式定位候选资源，再读取关键内容" + suffix;
            case "openviking_grep" -> "我先按内容搜索候选资源，再筛选需要读取的部分" + suffix;
            case "openviking_find" -> "我先做一次知识检索，找到相关候选内容" + suffix;
            case "openviking_search" -> "我先结合当前会话检索相关内容，再继续核对" + suffix;
            case "createPlan" -> "我先把任务拆成可执行步骤，再逐项推进。";
            default -> "我先获取必要信息，再继续推进。";
        };
    }

    private String strategyShiftContent(ToolExecutionRequest first) {
        if (SEARCH_TOOLS.contains(first.name())) {
            return "上一步结果还不够，我换用检索方式继续定位。";
        }
        return switch (first.name()) {
            case "openviking_list", "openviking_tree" -> "上一步结果还不够，我先回到目录结构重新定位。";
            case "openviking_read" -> "已经拿到更具体的目标，我继续读取关键资源。";
            default -> "上一步结果还不够，我换一种方法继续推进。";
        };
    }

    private boolean hasRecentInsufficientToolResult(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        int checked = 0;
        for (int i = messages.size() - 1; i >= 0 && checked < 4; i--) {
            ChatMessage message = messages.get(i);
            if (!(message instanceof ToolExecutionResultMessage toolResult)) {
                if (checked > 0) {
                    break;
                }
                continue;
            }
            checked++;
            String text = toolResult.text();
            if (isInsufficient(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsufficient(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.toLowerCase();
        return normalized.contains("result=empty")
                || normalized.contains("failed:")
                || normalized.contains("工具执行失败")
                || normalized.contains("empty response")
                || normalized.contains("not found")
                || normalized.contains("未找到");
    }

    private void publish(ChatRunTraceContext traceContext, Map<String, Object> payload) {
        timelineActionService.publish(traceContext, "assistant_checkpoint", payload, "AssistantCheckpointService");
    }
}
