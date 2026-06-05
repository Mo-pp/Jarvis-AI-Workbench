package com.msz.resume.ai.enterprise.tooling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业报销/OA Demo 工具包。
 *
 * <p>这些工具不连接真实 OA，只模拟“查制度 -> 解析材料 -> 规则校验 -> 创建草稿”的闭环，
 * 用于展示 JARVIS 相比普通 RAG 更能推进企业流程。
 */
@Slf4j
@Component
public class ExpenseDemoTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");

    @Tool("""
            Search the enterprise travel reimbursement policy for the expense/OA demo.
            Use this first when the user asks about 报销, 差旅, 出差费用, OA reimbursement, expense claim, or preparing an expense draft.
            This is a mock enterprise policy tool: it returns city caps, required materials, approval thresholds, and notes.
            """)
    public String searchExpensePolicy(
            @P("Business scenario, for example: travel, lodging, transport, meal, procurement, or reimbursement draft.") String scenario,
            @P(value = "Destination city or city tier, for example: 上海, 北京, 深圳. Optional.", required = false) String city,
            @P(value = "Comma-separated expense types to check, for example: lodging,intercity_transport,local_transport,meal. Optional.", required = false) String expenseTypes
    ) {
        log.info("[ExpenseDemoTool] searchExpensePolicy scenario={}, city={}, expenseTypes={}", scenario, city, expenseTypes);

        Policy policy = policyFor(city);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("tool", "searchExpensePolicy");
        response.put("scenario", blankToDefault(scenario, "travel_reimbursement"));
        response.put("city", blankToDefault(city, "未指定"));
        response.put("expenseTypes", blankToDefault(expenseTypes, "all_travel_expenses"));
        response.put("policy", policy);
        response.put("workflowHint", List.of(
                "Next call parseExpenseAttachment to parse receipts, itinerary, or demo materials.",
                "Then call checkExpenseRules with the policy JSON and parsed attachment JSON.",
                "After rule checking, call createExpenseDraft to create a mock OA draft and publish the returned markdown summary as a markdown artifact."
        ));
        return toJson(response);
    }

    @Tool("""
            Parse expense receipts, itinerary text, or a mock travel reimbursement case.
            Use preset=shanghai_trip only when the user explicitly asks for a demo/mock sample or says no real receipt is needed.
            If the user wants a real reimbursement check but provides no receipt/material text, return needs_user_input instead of inventing data.
            """)
    public String parseExpenseAttachment(
            @P(value = "Receipt, itinerary, or expense material text pasted by the user. Optional when preset is provided.", required = false) String attachmentText,
            @P(value = "Optional mock preset. Supported value: shanghai_trip. Use only for demo/mock flow.", required = false) String preset
    ) {
        log.info("[ExpenseDemoTool] parseExpenseAttachment preset={}, textLength={}",
                preset, attachmentText != null ? attachmentText.length() : 0);

        if (isShanghaiPreset(preset) || containsDemoPresetMarker(attachmentText)) {
            return toJson(buildShanghaiTripAttachment());
        }

        if (attachmentText == null || attachmentText.isBlank()) {
            return toJson(Map.of(
                    "status", "needs_user_input",
                    "tool", "parseExpenseAttachment",
                    "message", "缺少发票、行程单或费用材料文本，无法做真实报销校验。",
                    "requiredMaterials", List.of("出差审批单", "交通票据", "住宿发票/订单", "市内交通票据", "餐补或餐饮说明"),
                    "demoHint", "如果只是演示流程，请让用户确认可以使用 preset=shanghai_trip 的模拟票据。"
            ));
        }

        List<ExpenseItem> items = parseItemsFromText(attachmentText);
        if (items.isEmpty()) {
            return toJson(Map.of(
                    "status", "needs_user_input",
                    "tool", "parseExpenseAttachment",
                    "message", "没有从材料中识别到明确金额。请补充每项费用的类型和金额，或使用 preset=shanghai_trip 演示。"
            ));
        }

        ExpenseAttachment attachment = ExpenseAttachment.builder()
                .status("success")
                .tool("parseExpenseAttachment")
                .source("user_text")
                .trip(TripInfo.builder()
                        .city(inferCity(attachmentText))
                        .days(inferDays(attachmentText))
                        .purpose(inferPurpose(attachmentText))
                        .build())
                .items(items)
                .providedMaterials(List.of("用户粘贴的费用材料文本"))
                .missingMaterials(List.of("出差审批单", "原始票据附件"))
                .totalAmount(sum(items))
                .build();
        return toJson(attachment);
    }

    @Tool("""
            Check parsed expense materials against the enterprise reimbursement policy.
            Call this after searchExpensePolicy and parseExpenseAttachment. Pass the raw JSON strings returned by those tools.
            It returns reimbursable amount, material gaps, over-limit items, and manual review reasons.
            """)
    public String checkExpenseRules(
            @P("Raw JSON returned by searchExpensePolicy.") String policyJson,
            @P("Raw JSON returned by parseExpenseAttachment.") String attachmentJson,
            @P(value = "Extra trip context from the user, for example department, project, business purpose, or approver. Optional.", required = false) String tripContext
    ) {
        log.info("[ExpenseDemoTool] checkExpenseRules policyLength={}, attachmentLength={}",
                policyJson != null ? policyJson.length() : 0,
                attachmentJson != null ? attachmentJson.length() : 0);

        try {
            JsonNode policyRoot = OBJECT_MAPPER.readTree(policyJson);
            JsonNode attachmentRoot = OBJECT_MAPPER.readTree(attachmentJson);
            if ("needs_user_input".equals(attachmentRoot.path("status").asText())) {
                return toJson(Map.of(
                        "status", "needs_user_input",
                        "tool", "checkExpenseRules",
                        "message", attachmentRoot.path("message").asText("缺少费用材料，无法校验。")
                ));
            }

            Policy policy = readPolicy(policyRoot.path("policy"));
            TripInfo trip = readTrip(attachmentRoot.path("trip"));
            List<ExpenseItem> items = readItems(attachmentRoot.path("items"));
            List<String> materialGaps = readStrings(attachmentRoot.path("missingMaterials"));

            RuleCheckResult result = evaluate(policy, trip, items, materialGaps, tripContext);
            return toJson(result);
        } catch (Exception e) {
            log.warn("[ExpenseDemoTool] checkExpenseRules failed: {}", e.getMessage());
            return toJson(Map.of(
                    "status", "error",
                    "tool", "checkExpenseRules",
                    "message", "报销规则校验失败：" + e.getMessage()
            ));
        }
    }

    @Tool("""
            Create a mock OA expense draft after rule checking.
            This does not call a real OA system. It returns a draft id, approval route, and markdown summary.
            Use this only after checkExpenseRules, and publish the returned markdownSummary with publishArtifact(type=markdown) for the demo workbench.
            """)
    public String createExpenseDraft(
            @P("Employee name. Use the user's provided name if available, otherwise use 当前用户.") String employeeName,
            @P("Department, project, or cost center. Ask the user if this is required and missing.") String department,
            @P("Business purpose of the trip or expense.") String tripPurpose,
            @P("Raw JSON returned by checkExpenseRules.") String policyCheckJson,
            @P(value = "Raw JSON returned by parseExpenseAttachment. Optional but recommended.", required = false) String attachmentJson,
            @P(value = "Approver name. Optional.", required = false) String approver
    ) {
        log.info("[ExpenseDemoTool] createExpenseDraft employee={}, department={}, purpose={}",
                employeeName, department, tripPurpose);

        try {
            JsonNode checkRoot = OBJECT_MAPPER.readTree(policyCheckJson);
            BigDecimal requestedAmount = decimal(checkRoot.path("requestedAmount"), BigDecimal.ZERO);
            BigDecimal reimbursableAmount = decimal(checkRoot.path("reimbursableAmount"), BigDecimal.ZERO);
            List<String> issues = readStrings(checkRoot.path("issues"));
            List<String> materialGaps = readStrings(checkRoot.path("materialGaps"));
            boolean needsManualReview = checkRoot.path("needsManualReview").asBoolean(!issues.isEmpty() || !materialGaps.isEmpty());

            String city = "";
            if (attachmentJson != null && !attachmentJson.isBlank()) {
                JsonNode attachmentRoot = OBJECT_MAPPER.readTree(attachmentJson);
                city = attachmentRoot.path("trip").path("city").asText("");
            }

            String draftId = buildDraftId(employeeName, department, requestedAmount);
            String approvalRoute = buildApprovalRoute(department, approver, needsManualReview);
            String markdown = buildDraftMarkdown(
                    draftId,
                    employeeName,
                    department,
                    tripPurpose,
                    city,
                    requestedAmount,
                    reimbursableAmount,
                    issues,
                    materialGaps,
                    approvalRoute,
                    needsManualReview
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "mock_created");
            response.put("tool", "createExpenseDraft");
            response.put("mock", true);
            response.put("draftId", draftId);
            response.put("system", "Mock-OA");
            response.put("employeeName", blankToDefault(employeeName, "当前用户"));
            response.put("department", department);
            response.put("tripPurpose", tripPurpose);
            response.put("requestedAmount", requestedAmount);
            response.put("reimbursableAmount", reimbursableAmount);
            response.put("needsManualReview", needsManualReview);
            response.put("approvalRoute", approvalRoute);
            response.put("issues", issues);
            response.put("materialGaps", materialGaps);
            response.put("markdownSummary", markdown);
            response.put("nextAction", "Call publishArtifact with type=markdown and markdown=markdownSummary.");
            return toJson(response);
        } catch (Exception e) {
            log.warn("[ExpenseDemoTool] createExpenseDraft failed: {}", e.getMessage());
            return toJson(Map.of(
                    "status", "error",
                    "tool", "createExpenseDraft",
                    "message", "创建 Mock OA 报销草稿失败：" + e.getMessage()
            ));
        }
    }

    private Policy policyFor(String city) {
        String normalizedCity = city != null ? city.trim() : "";
        BigDecimal lodgingCap = switch (normalizedCity) {
            case "北京" -> money("600");
            case "上海" -> money("550");
            case "深圳", "广州" -> money("500");
            default -> money("450");
        };
        String cityTier = switch (normalizedCity) {
            case "北京", "上海", "深圳", "广州" -> "一线/高消费城市";
            case "" -> "默认城市";
            default -> "普通城市";
        };
        return Policy.builder()
                .cityTier(cityTier)
                .lodgingCapPerNight(lodgingCap)
                .mealSubsidyPerDay(money("100"))
                .localTransportCapPerDay(money("150"))
                .approvalThreshold(money("3000"))
                .requiredMaterials(List.of("出差审批单", "交通票据", "住宿发票/订单", "市内交通票据", "费用说明"))
                .manualReviewRules(List.of(
                        "单项费用超标准需要直属领导确认",
                        "材料缺失时只能创建草稿，不能自动提交",
                        "总金额超过 3000 元需要部门负责人二次审批"
                ))
                .notes(List.of(
                        "交通费用按真实票据报销",
                        "住宿按城市标准和实际晚数校验",
                        "餐补按出差天数定额计算，超出部分不自动报销"
                ))
                .build();
    }

    private ExpenseAttachment buildShanghaiTripAttachment() {
        List<ExpenseItem> items = List.of(
                ExpenseItem.builder()
                        .category("intercity_transport")
                        .name("广州南-上海虹桥 高铁二等座")
                        .date("2026-05-18")
                        .amount(money("553.50"))
                        .quantity(1)
                        .material("高铁电子客票")
                        .build(),
                ExpenseItem.builder()
                        .category("lodging")
                        .name("上海浦东商务酒店住宿")
                        .date("2026-05-18~2026-05-20")
                        .amount(money("1280"))
                        .quantity(2)
                        .material("住宿发票")
                        .build(),
                ExpenseItem.builder()
                        .category("local_transport")
                        .name("市内出租车/网约车")
                        .date("2026-05-18~2026-05-20")
                        .amount(money("236"))
                        .quantity(2)
                        .material("电子行程单")
                        .build(),
                ExpenseItem.builder()
                        .category("meal")
                        .name("出差餐费")
                        .date("2026-05-18~2026-05-20")
                        .amount(money("228"))
                        .quantity(2)
                        .material("餐饮发票")
                        .build()
        );
        return ExpenseAttachment.builder()
                .status("success")
                .tool("parseExpenseAttachment")
                .source("mockPreset:shanghai_trip")
                .trip(TripInfo.builder()
                        .city("上海")
                        .startDate("2026-05-18")
                        .endDate("2026-05-20")
                        .days(2)
                        .traveler("当前用户")
                        .department("研发部")
                        .purpose("客户现场技术支持")
                        .build())
                .items(items)
                .providedMaterials(List.of("高铁电子客票", "住宿发票", "市内交通电子行程单", "餐饮发票"))
                .missingMaterials(List.of("出差审批单", "酒店订单/水单截图"))
                .totalAmount(sum(items))
                .build();
    }

    private List<ExpenseItem> parseItemsFromText(String text) {
        List<ExpenseItem> items = new ArrayList<>();
        String[] lines = text.split("\\R+");
        int index = 1;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String amountSource = trimmed
                    .replaceAll("20\\d{2}[-./年]\\d{1,2}[-./月]\\d{1,2}", " ")
                    .replace(",", "");
            Matcher matcher = AMOUNT_PATTERN.matcher(amountSource);
            if (!matcher.find()) {
                continue;
            }
            BigDecimal amount = money(matcher.group(1));
            items.add(ExpenseItem.builder()
                    .category(inferCategory(trimmed))
                    .name(trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed)
                    .date(inferDate(trimmed))
                    .amount(amount)
                    .quantity(1)
                    .material("text-line-" + index)
                    .build());
            index++;
        }
        return items;
    }

    private RuleCheckResult evaluate(Policy policy,
                                     TripInfo trip,
                                     List<ExpenseItem> items,
                                     List<String> materialGaps,
                                     String tripContext) {
        int days = trip.getDays() != null && trip.getDays() > 0 ? trip.getDays() : 1;
        BigDecimal requestedAmount = sum(items);
        BigDecimal reimbursableAmount = BigDecimal.ZERO;
        List<String> issues = new ArrayList<>();
        List<Map<String, Object>> decisions = new ArrayList<>();

        BigDecimal localTransportTotal = sumByCategory(items, "local_transport");
        BigDecimal localTransportCap = policy.getLocalTransportCapPerDay().multiply(BigDecimal.valueOf(days));
        BigDecimal mealTotal = sumByCategory(items, "meal");
        BigDecimal mealCap = policy.getMealSubsidyPerDay().multiply(BigDecimal.valueOf(days));

        for (ExpenseItem item : items) {
            BigDecimal allowed = item.getAmount();
            String decision = "pass";
            String note = "按票据或制度标准纳入草稿";

            if ("lodging".equals(item.getCategory())) {
                int nights = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : days;
                BigDecimal cap = policy.getLodgingCapPerNight().multiply(BigDecimal.valueOf(nights));
                if (item.getAmount().compareTo(cap) > 0) {
                    allowed = cap;
                    decision = "partial";
                    note = "住宿超出城市标准 " + item.getAmount().subtract(cap) + " 元，需要人工确认";
                    issues.add(item.getName() + " 超出住宿标准 " + item.getAmount().subtract(cap) + " 元");
                }
            } else if ("local_transport".equals(item.getCategory()) && localTransportTotal.compareTo(localTransportCap) > 0) {
                allowed = prorate(item.getAmount(), localTransportTotal, localTransportCap);
                decision = "partial";
                note = "市内交通合计超出标准，需要人工确认";
                if (issues.stream().noneMatch(issue -> issue.contains("市内交通"))) {
                    issues.add("市内交通合计超出标准 " + localTransportTotal.subtract(localTransportCap) + " 元");
                }
            } else if ("meal".equals(item.getCategory()) && mealTotal.compareTo(mealCap) > 0) {
                allowed = prorate(item.getAmount(), mealTotal, mealCap);
                decision = "partial";
                note = "餐补按出差天数定额计算，超出部分不自动报销";
                if (issues.stream().noneMatch(issue -> issue.contains("餐补"))) {
                    issues.add("餐费/餐补超出定额 " + mealTotal.subtract(mealCap) + " 元");
                }
            }

            reimbursableAmount = reimbursableAmount.add(allowed);
            decisions.add(Map.of(
                    "category", item.getCategory(),
                    "name", item.getName(),
                    "requestedAmount", item.getAmount(),
                    "approvedAmount", allowed,
                    "decision", decision,
                    "note", note
            ));
        }

        if (!materialGaps.isEmpty()) {
            issues.add("材料缺失：" + String.join("、", materialGaps));
        }
        if (requestedAmount.compareTo(policy.getApprovalThreshold()) > 0) {
            issues.add("申请总金额超过 " + policy.getApprovalThreshold() + " 元，需要部门负责人二次审批");
        }

        return RuleCheckResult.builder()
                .status("success")
                .tool("checkExpenseRules")
                .tripCity(trip.getCity())
                .tripDays(days)
                .tripContext(tripContext)
                .requestedAmount(requestedAmount.setScale(2, RoundingMode.HALF_UP))
                .reimbursableAmount(reimbursableAmount.setScale(2, RoundingMode.HALF_UP))
                .needsManualReview(!issues.isEmpty())
                .issues(issues)
                .materialGaps(materialGaps)
                .decisions(decisions)
                .recommendation(issues.isEmpty()
                        ? "材料和金额符合规则，可创建 OA 草稿。"
                        : "建议创建 OA 草稿并标记为待补充/待人工确认，暂不自动提交。")
                .build();
    }

    private String buildDraftMarkdown(String draftId,
                                      String employeeName,
                                      String department,
                                      String tripPurpose,
                                      String city,
                                      BigDecimal requestedAmount,
                                      BigDecimal reimbursableAmount,
                                      List<String> issues,
                                      List<String> materialGaps,
                                      String approvalRoute,
                                      boolean needsManualReview) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Mock OA 报销草稿\n\n");
        markdown.append("- 草稿编号：").append(draftId).append("\n");
        markdown.append("- 申请人：").append(blankToDefault(employeeName, "当前用户")).append("\n");
        markdown.append("- 部门/项目：").append(blankToDefault(department, "未填写")).append("\n");
        markdown.append("- 出差城市：").append(blankToDefault(city, "未识别")).append("\n");
        markdown.append("- 事由：").append(blankToDefault(tripPurpose, "未填写")).append("\n");
        markdown.append("- 申请金额：").append(requestedAmount).append(" 元\n");
        markdown.append("- 建议可报销金额：").append(reimbursableAmount).append(" 元\n");
        markdown.append("- 状态：").append(needsManualReview ? "待补材料/待人工确认" : "可提交审批").append("\n");
        markdown.append("- 审批路线：").append(approvalRoute).append("\n\n");

        markdown.append("## 规则校验结果\n\n");
        if (issues.isEmpty()) {
            markdown.append("- 未发现超标或规则冲突。\n");
        } else {
            for (String issue : issues) {
                markdown.append("- ").append(issue).append("\n");
            }
        }

        markdown.append("\n## 材料缺口\n\n");
        if (materialGaps.isEmpty()) {
            markdown.append("- 材料齐全。\n");
        } else {
            for (String gap : materialGaps) {
                markdown.append("- ").append(gap).append("\n");
            }
        }

        markdown.append("\n## 下一步\n\n");
        markdown.append(needsManualReview
                ? "- 请补齐缺失材料，并由审批人确认超标部分是否允许报销。\n"
                : "- 可在 OA 中提交审批。\n");
        markdown.append("\n> 这是 JARVIS 企业端 Demo 的 Mock OA 草稿，没有写入真实系统。\n");
        return markdown.toString();
    }

    private Policy readPolicy(JsonNode node) {
        return Policy.builder()
                .cityTier(node.path("cityTier").asText("默认城市"))
                .lodgingCapPerNight(decimal(node.path("lodgingCapPerNight"), money("450")))
                .mealSubsidyPerDay(decimal(node.path("mealSubsidyPerDay"), money("100")))
                .localTransportCapPerDay(decimal(node.path("localTransportCapPerDay"), money("150")))
                .approvalThreshold(decimal(node.path("approvalThreshold"), money("3000")))
                .requiredMaterials(readStrings(node.path("requiredMaterials")))
                .manualReviewRules(readStrings(node.path("manualReviewRules")))
                .notes(readStrings(node.path("notes")))
                .build();
    }

    private TripInfo readTrip(JsonNode node) {
        return TripInfo.builder()
                .city(node.path("city").asText(""))
                .startDate(node.path("startDate").asText(null))
                .endDate(node.path("endDate").asText(null))
                .days(node.path("days").isNumber() ? node.path("days").asInt() : null)
                .traveler(node.path("traveler").asText(null))
                .department(node.path("department").asText(null))
                .purpose(node.path("purpose").asText(null))
                .build();
    }

    private List<ExpenseItem> readItems(JsonNode node) {
        List<ExpenseItem> items = new ArrayList<>();
        if (!node.isArray()) {
            return items;
        }
        for (JsonNode itemNode : node) {
            items.add(ExpenseItem.builder()
                    .category(itemNode.path("category").asText("other"))
                    .name(itemNode.path("name").asText("未命名费用"))
                    .date(itemNode.path("date").asText(null))
                    .amount(decimal(itemNode.path("amount"), BigDecimal.ZERO))
                    .quantity(itemNode.path("quantity").isNumber() ? itemNode.path("quantity").asInt() : 1)
                    .material(itemNode.path("material").asText(null))
                    .build());
        }
        return items;
    }

    private List<String> readStrings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode value : node) {
            if (!value.asText("").isBlank()) {
                values.add(value.asText());
            }
        }
        return values;
    }

    private BigDecimal sum(List<ExpenseItem> items) {
        return items.stream()
                .map(ExpenseItem::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumByCategory(List<ExpenseItem> items, String category) {
        return items.stream()
                .filter(item -> category.equals(item.getCategory()))
                .map(ExpenseItem::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal prorate(BigDecimal itemAmount, BigDecimal total, BigDecimal cap) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return itemAmount.multiply(cap).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return node.decimalValue().setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return money(node.asText());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
    }

    private String inferCategory(String line) {
        if (line.contains("住宿") || line.contains("酒店")) {
            return "lodging";
        }
        if (line.contains("高铁") || line.contains("火车") || line.contains("机票") || line.contains("飞机")) {
            return "intercity_transport";
        }
        if (line.contains("打车") || line.contains("出租") || line.contains("网约车") || line.contains("市内")) {
            return "local_transport";
        }
        if (line.contains("餐") || line.contains("饭")) {
            return "meal";
        }
        return "other";
    }

    private String inferDate(String line) {
        Matcher matcher = Pattern.compile("(20\\d{2}[-./年]\\d{1,2}[-./月]\\d{1,2})").matcher(line);
        return matcher.find() ? matcher.group(1).replace('年', '-').replace('月', '-') : null;
    }

    private String inferCity(String text) {
        for (String city : List.of("上海", "北京", "深圳", "广州", "杭州", "成都", "武汉")) {
            if (text.contains(city)) {
                return city;
            }
        }
        return "";
    }

    private int inferDays(String text) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*天").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    private String inferPurpose(String text) {
        if (text.contains("客户")) {
            return "客户拜访/现场支持";
        }
        if (text.contains("培训")) {
            return "培训学习";
        }
        if (text.contains("会议")) {
            return "会议出差";
        }
        return "业务出差";
    }

    private boolean isShanghaiPreset(String preset) {
        return preset != null && List.of("shanghai_trip", "demo_shanghai_trip", "上海出差").contains(preset.trim().toLowerCase(Locale.ROOT));
    }

    private boolean containsDemoPresetMarker(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("demo_shanghai_travel");
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String buildDraftId(String employeeName, String department, BigDecimal requestedAmount) {
        int hash = Math.abs((blankToDefault(employeeName, "user") + "|" + blankToDefault(department, "dept") + "|" + requestedAmount).hashCode());
        return "EXP-DEMO-" + LocalDate.now().toString().replace("-", "") + "-" + String.format("%04d", hash % 10000);
    }

    private String buildApprovalRoute(String department, String approver, boolean needsManualReview) {
        List<String> route = new ArrayList<>();
        route.add(blankToDefault(department, "申请部门") + "直属负责人");
        if (needsManualReview) {
            route.add("财务复核");
        }
        if (approver != null && !approver.isBlank()) {
            route.add(approver.trim());
        }
        return String.join(" -> ", route);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"message\":\"JSON serialization failed\"}";
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Policy {
        private String cityTier;
        private BigDecimal lodgingCapPerNight;
        private BigDecimal mealSubsidyPerDay;
        private BigDecimal localTransportCapPerDay;
        private BigDecimal approvalThreshold;
        private List<String> requiredMaterials;
        private List<String> manualReviewRules;
        private List<String> notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TripInfo {
        private String city;
        private String startDate;
        private String endDate;
        private Integer days;
        private String traveler;
        private String department;
        private String purpose;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExpenseItem {
        private String category;
        private String name;
        private String date;
        private BigDecimal amount;
        private Integer quantity;
        private String material;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExpenseAttachment {
        private String status;
        private String tool;
        private String source;
        private TripInfo trip;
        private List<ExpenseItem> items;
        private List<String> providedMaterials;
        private List<String> missingMaterials;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RuleCheckResult {
        private String status;
        private String tool;
        private String tripCity;
        private Integer tripDays;
        private String tripContext;
        private BigDecimal requestedAmount;
        private BigDecimal reimbursableAmount;
        private Boolean needsManualReview;
        private List<String> issues;
        private List<String> materialGaps;
        private List<Map<String, Object>> decisions;
        private String recommendation;
    }
}
