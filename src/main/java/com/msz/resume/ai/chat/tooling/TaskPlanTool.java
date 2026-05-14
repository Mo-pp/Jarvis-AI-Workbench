package com.msz.resume.ai.chat.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 任务规划工具（核心工具）
 *
 * <p>LLM 通过此工具创建和管理 Jarvis 自己正在执行的内部任务计划，
 * 支持拆解复杂执行工作为多个子任务并追踪进度。
 * 工具内部使用 ThreadLocal 维护当前请求的任务列表状态，
 * ExecuteToolNode 在工具调用前后负责与 QueryLoopState.TASK_PLAN 的同步。
 *
 * <p>核心工具会在每轮 LLM 请求中暴露完整 schema，便于模型在处理复杂任务时主动维护计划。
 *
 * <h2>工具方法</h2>
 * <ul>
 *   <li>{@link #createPlan}: 创建任务计划（传入任务描述列表）</li>
 *   <li>{@link #updateStatus}: 更新单个任务的状态</li>
 *   <li>{@link #addTask}: 追加新任务</li>
 *   <li>{@link #removeTask}: 移除任务</li>
 * </ul>
 */
@Slf4j
@CoreTool
@Component
public class TaskPlanTool {

    /**
     * ThreadLocal 存储当前请求的任务列表
     * ExecuteToolNode 在调用前从 QueryLoopState.TASK_PLAN 初始化，
     * 调用后将最新任务列表写回 QueryLoopState
     */
    private static final ThreadLocal<List<Map<String, Object>>> CURRENT_TASKS =
            ThreadLocal.withInitial(ArrayList::new);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> TASK_LIST_TYPE =
            new TypeReference<>() {};

    private static final AtomicInteger TASK_COUNTER = new AtomicInteger(0);
    private static final Pattern USER_PLAN_STAGE_PATTERN = Pattern.compile(
            "(阶段|phase\\s*\\d+|第[一二三四五六七八九十0-9]+[阶段周月天步])",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> USER_PLAN_MARKERS = List.of(
            "学习计划", "学习路线", "成长路径", "职业规划", "路线图", "roadmap",
            "复习表", "时间表", "日程", "课程", "岗位进阶", "技能提升"
    );

    // ==================== ThreadLocal 管理方法（供 ExecuteToolNode 调用）====================

    /**
     * 初始化 ThreadLocal 任务列表（从 QueryLoopState.TASK_PLAN 恢复）
     *
     * @param tasks 当前任务列表
     */
    public static void initTasks(List<Map<String, Object>> tasks) {
        CURRENT_TASKS.set(copyTasks(tasks));
        syncTaskCounter(tasks);
    }

    /**
     * 获取当前任务列表的快照（供 ExecuteToolNode 读取）
     *
     * @return 当前任务列表的副本
     */
    public static List<Map<String, Object>> getCurrentTasks() {
        return copyTasks(CURRENT_TASKS.get());
    }

    /**
     * 将当前未结束的内部任务统一收口为 completed。
     *
     * <p>用于像 publishArtifact 这类“终态产物已成功产出”的场景。此时如果任务栏里仍残留
     * pending / in_progress，大多只是 LLM 来不及补最后一次 updateStatus，不应继续向前端暴露半完成状态。
     */
    public static List<Map<String, Object>> completeUnfinishedTasks() {
        List<Map<String, Object>> tasks = CURRENT_TASKS.get();
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        long now = System.currentTimeMillis();
        for (Map<String, Object> task : tasks) {
            String status = String.valueOf(task.getOrDefault("status", "pending"));
            if (!"completed".equals(status) && !"skipped".equals(status)) {
                task.put("status", "completed");
                task.put("updatedAt", now);
            }
        }
        return copyTasks(tasks);
    }

    /**
     * 清理 ThreadLocal（请求结束后必须调用，防止内存泄漏）
     */
    public static void clearTasks() {
        CURRENT_TASKS.remove();
    }

    // ==================== @Tool 工具方法 ====================

    /**
     * 创建内部执行任务计划
     *
     * <p>传入 Jarvis 要执行的任务描述列表，自动生成 taskId 和时间戳。已有任务计划时覆盖。
     *
     * @param tasksJson JSON 数组，每个元素包含 description（必填）和 detail（可选）
     * @return 当前完整任务列表的 JSON 字符串
     */
    @Tool("""
            创建 Jarvis 内部执行任务栏。仅当 Jarvis 本轮需要实际执行中长任务时使用，例如跨文件代码修改、Bug 排查、代码库分析、多步骤工具调用、实现/调试/重构/验证。
            禁止用于承载用户要求生成的内容计划：学习计划、职业规划、路线图、roadmap、日程表、复习表、方案大纲、文章大纲等必须写在最终回复正文里，不要调用本工具。
            每个任务必须是 Jarvis 自己要做的可执行动作，例如“检查后端导出流程”“修改前端消息渲染”“运行构建验证”；不要写成用户计划阶段，例如“阶段一：AI/LLM 基础知识学习”。
            传入任务列表 JSON 数组，每个任务包含 description（描述，必填）和 detail（详细说明，可选）。已有内部任务计划时将被覆盖。返回当前完整内部任务列表。
            """)
    public String createPlan(@P("Jarvis 内部执行任务列表 JSON 数组；禁止传入用户学习计划、职业路线图、日程安排、方案大纲等要展示给用户的内容计划。") String tasksJson) {
        log.info("[TaskPlanTool] createPlan, tasksJson length: {}",
                tasksJson != null ? tasksJson.length() : 0);

        try {
            List<Map<String, Object>> inputTasks = OBJECT_MAPPER.readValue(
                    tasksJson, TASK_LIST_TYPE);

            if (looksLikeUserFacingPlan(inputTasks)) {
                log.warn("[TaskPlanTool] 拒绝将用户内容计划写入内部任务栏");
                return "错误：createPlan 只用于 Jarvis 内部执行任务栏。当前输入看起来是用户要求生成的内容计划/学习路线/路线图，请不要写入任务栏，改为在最终回复正文输出给用户。";
            }

            List<Map<String, Object>> tasks = new ArrayList<>();
            for (Map<String, Object> inputTask : inputTasks) {
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("taskId", "task-" + TASK_COUNTER.incrementAndGet());
                task.put("description", String.valueOf(inputTask.getOrDefault("description", "")));
                task.put("detail", String.valueOf(inputTask.getOrDefault("detail", "")));
                task.put("status", "pending");
                task.put("createdAt", System.currentTimeMillis());
                task.put("updatedAt", System.currentTimeMillis());
                tasks.add(task);
            }

            CURRENT_TASKS.set(tasks);
            log.info("[TaskPlanTool] 创建任务计划成功, 任务数: {}", tasks.size());
            return toJson(tasks);

        } catch (Exception e) {
            log.error("[TaskPlanTool] createPlan 失败", e);
            return "错误：创建任务计划失败 - " + e.getMessage();
        }
    }

    /**
     * 更新任务状态
     *
     * @param taskId    任务ID
     * @param newStatus 新状态: pending / in_progress / completed / skipped
     * @return 当前完整任务列表的 JSON 字符串
     */
    @Tool("更新 Jarvis 内部执行任务状态。只用于任务栏中的 Jarvis 自己执行步骤，不用于用户学习计划/路线图/日程表等内容计划。参数：taskId（任务ID），newStatus（新状态：pending/in_progress/completed/skipped）。返回当前完整内部任务列表。")
    public String updateStatus(
            @P("taskId") String taskId,
            @P("newStatus") String newStatus) {
        log.info("[TaskPlanTool] updateStatus: taskId={}, newStatus={}", taskId, newStatus);

        List<Map<String, Object>> tasks = CURRENT_TASKS.get();
        if (tasks.isEmpty()) {
            return "错误：当前没有任务计划，请先使用 createPlan 创建。";
        }

        for (Map<String, Object> task : tasks) {
            if (taskId.equals(task.get("taskId"))) {
                task.put("status", newStatus);
                task.put("updatedAt", System.currentTimeMillis());
                log.info("[TaskPlanTool] 更新任务状态成功: {} -> {}", taskId, newStatus);
                return toJson(tasks);
            }
        }

        return "错误：未找到 taskId=" + taskId + " 的任务。当前任务列表: " + toJson(tasks);
    }

    /**
     * 添加新任务
     *
     * @param description 任务描述
     * @param detail      任务详细说明（可选）
     * @return 当前完整任务列表的 JSON 字符串
     */
    @Tool("在 Jarvis 内部执行任务栏中追加一个新任务。只追加 Jarvis 自己接下来要执行的动作，不追加用户计划阶段或正文内容章节。参数：description（描述，必填），detail（详细说明，可选）。返回当前完整内部任务列表。")
    public String addTask(
            @P("description") String description,
            @P(value = "detail", required = false) String detail) {
        log.info("[TaskPlanTool] addTask: description={}", description);

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("taskId", "task-" + TASK_COUNTER.incrementAndGet());
        task.put("description", description);
        task.put("detail", detail != null ? detail : "");
        task.put("status", "pending");
        task.put("createdAt", System.currentTimeMillis());
        task.put("updatedAt", System.currentTimeMillis());

        List<Map<String, Object>> tasks = CURRENT_TASKS.get();
        tasks.add(task);
        log.info("[TaskPlanTool] 添加任务成功, 当前总数: {}", tasks.size());
        return toJson(tasks);
    }

    /**
     * 移除任务
     *
     * @param taskId 要移除的任务ID
     * @return 当前完整任务列表的 JSON 字符串
     */
    @Tool("从 Jarvis 内部执行任务栏中移除一个任务。只用于维护 Jarvis 自己的执行进度，不用于编辑用户计划内容。参数：taskId（要移除的任务ID）。返回当前完整内部任务列表。")
    public String removeTask(
            @P("taskId") String taskId) {
        log.info("[TaskPlanTool] removeTask: taskId={}", taskId);

        List<Map<String, Object>> tasks = CURRENT_TASKS.get();
        boolean removed = tasks.removeIf(task -> taskId.equals(task.get("taskId")));

        if (removed) {
            log.info("[TaskPlanTool] 移除任务成功, 当前总数: {}", tasks.size());
        } else {
            log.warn("[TaskPlanTool] 未找到要移除的任务: {}", taskId);
        }

        return toJson(tasks);
    }

    // ==================== 内部方法 ====================

    private String toJson(List<Map<String, Object>> tasks) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
        } catch (JsonProcessingException e) {
            log.error("[TaskPlanTool] JSON 序列化失败", e);
            return "[]";
        }
    }

    private boolean looksLikeUserFacingPlan(List<Map<String, Object>> inputTasks) {
        if (inputTasks == null || inputTasks.isEmpty()) {
            return false;
        }

        for (Map<String, Object> inputTask : inputTasks) {
            String text = String.valueOf(inputTask.getOrDefault("description", ""))
                    + " "
                    + String.valueOf(inputTask.getOrDefault("detail", ""));
            if (isUserFacingPlanText(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUserFacingPlanText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = text.toLowerCase();
        boolean hasStageLabel = USER_PLAN_STAGE_PATTERN.matcher(normalized).find();
        boolean hasUserPlanMarker = USER_PLAN_MARKERS.stream().anyMatch(normalized::contains);

        return hasUserPlanMarker
                || hasStageLabel && containsAny(normalized, "学习", "路线", "roadmap", "课程", "复习", "进阶", "技能", "职业");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static void syncTaskCounter(List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        int maxTaskNumber = 0;
        for (Map<String, Object> task : tasks) {
            Object taskIdValue = task.get("taskId");
            if (!(taskIdValue instanceof String taskId) || !taskId.startsWith("task-")) {
                continue;
            }

            try {
                int taskNumber = Integer.parseInt(taskId.substring("task-".length()));
                maxTaskNumber = Math.max(maxTaskNumber, taskNumber);
            } catch (NumberFormatException ignored) {
                // Ignore custom task ids. They do not participate in generated task id sequencing.
            }
        }

        int finalMaxTaskNumber = maxTaskNumber;
        TASK_COUNTER.updateAndGet(current -> Math.max(current, finalMaxTaskNumber));
    }

    private static List<Map<String, Object>> copyTasks(List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> copiedTasks = new ArrayList<>(tasks.size());
        for (Map<String, Object> task : tasks) {
            copiedTasks.add(new LinkedHashMap<>(task));
        }
        return copiedTasks;
    }
}
