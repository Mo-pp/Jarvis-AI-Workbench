package com.msz.resume.ai.chat.runtime.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolActionEventServiceTest {

    private final ToolActionEventService service = new ToolActionEventService(
            new ObjectMapper(),
            new TimelineActionService()
    );

    @Test
    @DisplayName("未知工具使用安全通用展示并过滤敏感参数")
    @SuppressWarnings("unchecked")
    void unknownToolUsesGenericDisplayAndSafePreview() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_unknown")
                .name("customTool")
                .arguments("""
                        {"query":"查找内容","password":"secret","api_key":"key","limit":5}
                        """)
                .build();

        Map<String, Object> payload = service.previewPayloadForTest(request, "success", "工具执行完成", null);
        Map<String, Object> preview = (Map<String, Object>) payload.get("preview");

        assertEquals("customTool", payload.get("title"));
        assertEquals("customTool", payload.get("description"));
        assertEquals("customTool", payload.get("groupKind"));
        assertEquals(true, payload.get("persistable"));
        assertEquals(false, payload.get("promptVisible"));
        assertEquals(false, payload.get("sensitive"));
        assertEquals("查找内容", preview.get("query"));
        assertEquals("5", preview.get("limit"));
        assertFalse(preview.containsKey("password"));
        assertFalse(preview.containsKey("api_key"));
    }

    @Test
    @DisplayName("任务计划工具生成用户可读摘要和分组")
    void taskPlanToolHasReadableDisplayMetadata() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_plan")
                .name("updateStatus")
                .arguments("""
                        {"taskId":"task-1","newStatus":"completed"}
                        """)
                .build();

        Map<String, Object> payload = service.previewPayloadForTest(request, "success", "任务状态已更新，共 3 个任务", null);

        assertEquals("更新任务状态", payload.get("title"));
        assertEquals("维护执行计划", payload.get("groupTitle"));
        assertEquals("task_plan", payload.get("groupKind"));
        assertEquals("任务：task-1 → completed", payload.get("description"));
    }

    @Test
    @DisplayName("Hook 阻断工具展示为 blocked 状态")
    void blockedToolUsesBlockedStatus() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_blocked")
                .name("spawnAgent")
                .arguments("""
                        {"subagentType":"general","maxTurns":3}
                        """)
                .build();

        Map<String, Object> payload = service.previewPayloadForTest(
                request,
                "blocked",
                null,
                "操作已被安全规则阻止：子 Agent 不允许继续派生"
        );

        assertEquals("blocked", payload.get("status"));
        assertEquals("操作已被安全规则阻止", payload.get("groupSummary"));
        assertTrue(String.valueOf(payload.get("error")).contains("安全规则"));
    }

    @Test
    @DisplayName("进度 delta 复用同一个稳定 action id")
    void progressDeltaUsesStableActionId() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_read")
                .name("openviking_read")
                .arguments("""
                        {"uri":"viking://memory/a.md","level":"full"}
                        """)
                .build();

        Map<String, Object> started = service.previewPayloadForTest(request, "running", null, null);
        Map<String, Object> delta = service.previewPayloadForTest(request, "running", "正在读取资源内容", null);

        assertEquals(started.get("id"), delta.get("id"));
        assertEquals("running", delta.get("status"));
        assertEquals("正在读取资源内容", delta.get("summary"));
        assertEquals("读取关键资源", delta.get("groupTitle"));
    }

    @Test
    @DisplayName("OpenViking 资源工具携带可展示路径")
    @SuppressWarnings("unchecked")
    void openVikingToolCarriesResourceUris() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_read")
                .name("openviking_read")
                .arguments("""
                        {"uri":"viking://resources/docs/JARVIS.md","level":"read","api_key":"hidden"}
                        """)
                .build();

        Map<String, Object> payload = service.previewPayloadForTest(request, "success", "资源读取完成", null);
        List<String> resourceUris = (List<String>) payload.get("resourceUris");
        Map<String, Object> preview = (Map<String, Object>) payload.get("preview");

        assertEquals(List.of("viking://resources/docs/JARVIS.md"), resourceUris);
        assertEquals("viking://resources/docs/JARVIS.md", preview.get("uri"));
        assertFalse(preview.containsKey("api_key"));
    }

    @Test
    @DisplayName("Skill 文件读取携带合成后的资源路径")
    @SuppressWarnings("unchecked")
    void skillReadFileCarriesSyntheticResourceUri() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_skill_file")
                .name("openviking_skill_read_file")
                .arguments("""
                        {"name":"ui-ux-pro-max","path":"references/glass.md"}
                        """)
                .build();

        Map<String, Object> payload = service.previewPayloadForTest(request, "success", "Skill 文件已读取", null);
        List<String> resourceUris = (List<String>) payload.get("resourceUris");

        assertEquals("读取 Skill 文件", payload.get("title"));
        assertEquals("处理 Skill", payload.get("groupTitle"));
        assertEquals(List.of("viking://agent/skills/ui-ux-pro-max/references/glass.md"), resourceUris);
    }

    @Test
    @DisplayName("报销 Demo 工具有企业流程分组和可读摘要")
    void expenseDemoToolHasEnterpriseWorkflowDisplay() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call_expense")
                .name("createExpenseDraft")
                .arguments("""
                        {"employeeName":"莫仕铮","department":"研发部","tripPurpose":"客户现场技术支持","approver":"张经理"}
                        """)
                .build();

        Map<String, Object> payload = service.previewPayloadForTest(
                request,
                "success",
                service.summarizeResultForTest(
                        "createExpenseDraft",
                        """
                                {"status":"mock_created","draftId":"EXP-DEMO-20260526-0001"}
                                """
                ),
                null
        );

        assertEquals("创建 OA 草稿", payload.get("title"));
        assertEquals("处理企业报销流程", payload.get("groupTitle"));
        assertEquals("expense_demo", payload.get("groupKind"));
        assertEquals("申请人：莫仕铮 · 部门：研发部", payload.get("description"));
        assertEquals("Mock OA 草稿已创建：EXP-DEMO-20260526-0001", payload.get("summary"));
    }
}
