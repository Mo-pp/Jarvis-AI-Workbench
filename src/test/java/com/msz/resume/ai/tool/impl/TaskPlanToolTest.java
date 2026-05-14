package com.msz.resume.ai.chat.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPlanToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TypeReference<List<Map<String, Object>>> taskListType = new TypeReference<>() {};

    @Test
    @DisplayName("从已恢复任务计划初始化后可以继续更新任务状态")
    void updateStatusAfterInitTasks() throws JsonProcessingException {
        TaskPlanTool.initTasks(List.of(task("task-100", "收集需求", "pending")));

        try {
            String result = new TaskPlanTool().updateStatus("task-100", "completed");
            List<Map<String, Object>> tasks = objectMapper.readValue(result, taskListType);

            assertEquals(1, tasks.size());
            assertEquals("task-100", tasks.get(0).get("taskId"));
            assertEquals("completed", tasks.get(0).get("status"));
        } finally {
            TaskPlanTool.clearTasks();
        }
    }

    @Test
    @DisplayName("从已恢复任务计划初始化后追加任务不会生成重复 taskId")
    void addTaskAfterInitTasksUsesNextTaskId() throws JsonProcessingException {
        TaskPlanTool.initTasks(List.of(task("task-100", "收集需求", "pending")));

        try {
            String result = new TaskPlanTool().addTask("整理报告", "");
            List<Map<String, Object>> tasks = objectMapper.readValue(result, taskListType);
            String newTaskId = String.valueOf(tasks.get(1).get("taskId"));

            assertEquals(2, tasks.size());
            assertTrue(newTaskId.startsWith("task-"));
            assertTrue(Integer.parseInt(newTaskId.substring("task-".length())) > 100);
        } finally {
            TaskPlanTool.clearTasks();
        }
    }

    @Test
    @DisplayName("createPlan 工具描述应明确禁止承载用户内容计划")
    void createPlanDescriptionShouldDistinguishInternalTasksFromUserPlans() {
        ToolSpecification createPlanSpec = ToolSpecifications.toolSpecificationsFrom(new TaskPlanTool()).stream()
                .filter(spec -> "createPlan".equals(spec.name()))
                .findFirst()
                .orElseThrow();

        String description = createPlanSpec.description();

        assertTrue(description.contains("Jarvis 内部执行任务栏"));
        assertTrue(description.contains("禁止用于承载用户要求生成的内容计划"));
        assertTrue(description.contains("学习计划"));
        assertTrue(description.contains("路线图"));
        assertFalse(description.contains("创建任务计划。传入任务列表JSON数组"));
    }

    @Test
    @DisplayName("createPlan 拒绝把用户学习计划写入内部任务栏")
    void createPlanShouldRejectUserFacingLearningPlan() {
        TaskPlanTool.initTasks(List.of());

        try {
            String result = new TaskPlanTool().createPlan("""
                    [
                      {"description":"阶段一：AI/LLM 基础知识学习","detail":"学习大语言模型原理、Transformer 架构和 Prompt Engineering 基础"},
                      {"description":"阶段二：AI 编程工具深度使用","detail":"熟悉 Copilot、Cursor、Claude Code 等工具"}
                    ]
                    """);

            assertTrue(result.contains("只用于 Jarvis 内部执行任务栏"));
            assertTrue(result.contains("最终回复正文"));
            assertTrue(TaskPlanTool.getCurrentTasks().isEmpty());
        } finally {
            TaskPlanTool.clearTasks();
        }
    }

    @Test
    @DisplayName("artifact 成功后可将未结束任务统一收口为 completed")
    void completeUnfinishedTasksShouldClosePendingAndInProgressTasks() {
        TaskPlanTool.initTasks(List.of(
                task("task-1", "整理字段", "completed"),
                task("task-2", "生成简历", "in_progress"),
                task("task-3", "发布到工作台", "pending"),
                task("task-4", "无需执行", "skipped")
        ));

        try {
            List<Map<String, Object>> tasks = TaskPlanTool.completeUnfinishedTasks();

            assertEquals("completed", tasks.get(0).get("status"));
            assertEquals("completed", tasks.get(1).get("status"));
            assertEquals("completed", tasks.get(2).get("status"));
            assertEquals("skipped", tasks.get(3).get("status"));
        } finally {
            TaskPlanTool.clearTasks();
        }
    }

    private Map<String, Object> task(String taskId, String description, String status) {
        return Map.of(
                "taskId", taskId,
                "description", description,
                "detail", "",
                "status", status,
                "createdAt", 1L,
                "updatedAt", 1L
        );
    }
}
