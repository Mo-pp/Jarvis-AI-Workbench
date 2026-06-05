package com.msz.resume.ai.enterprise.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseDemoToolTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExpenseDemoTool tool = new ExpenseDemoTool();

    @Test
    @DisplayName("报销 Demo 工具链可创建 Mock OA 草稿")
    void expenseDemoFlowShouldCreateMockOaDraft() throws Exception {
        String policy = tool.searchExpensePolicy("travel", "上海", "lodging,transport,meal");
        String attachment = tool.parseExpenseAttachment(null, "shanghai_trip");
        String check = tool.checkExpenseRules(policy, attachment, "研发部客户现场技术支持");
        String draft = tool.createExpenseDraft("莫仕铮", "研发部", "客户现场技术支持", check, attachment, "张经理");

        JsonNode policyRoot = OBJECT_MAPPER.readTree(policy);
        JsonNode attachmentRoot = OBJECT_MAPPER.readTree(attachment);
        JsonNode checkRoot = OBJECT_MAPPER.readTree(check);
        JsonNode draftRoot = OBJECT_MAPPER.readTree(draft);

        assertEquals("success", policyRoot.path("status").asText());
        assertEquals("上海", attachmentRoot.path("trip").path("city").asText());
        assertEquals("success", checkRoot.path("status").asText());
        assertTrue(checkRoot.path("requestedAmount").decimalValue().doubleValue() > 0);
        assertTrue(checkRoot.path("reimbursableAmount").decimalValue().doubleValue() > 0);
        assertFalse(checkRoot.path("materialGaps").isEmpty());
        assertEquals("mock_created", draftRoot.path("status").asText());
        assertTrue(draftRoot.path("draftId").asText().startsWith("EXP-DEMO-"));
        assertTrue(draftRoot.path("markdownSummary").asText().contains("Mock OA 报销草稿"));
        assertTrue(draftRoot.path("markdownSummary").asText().contains("材料缺口"));
    }

    @Test
    @DisplayName("缺少材料时解析工具返回 needs_user_input 而不是编造票据")
    void parseExpenseAttachmentShouldRequireInputWhenNoTextOrPreset() throws Exception {
        String result = tool.parseExpenseAttachment(null, null);

        JsonNode root = OBJECT_MAPPER.readTree(result);

        assertEquals("needs_user_input", root.path("status").asText());
        assertTrue(root.path("message").asText().contains("缺少"));
    }
}
