package com.msz.resume.ai.chat.runtime.node.inner.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.runtime.state.QueryLoopState;
import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import com.msz.resume.ai.chat.tooling.AskUserQuestionParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户问答 artifact 策略
 *
 * <p>负责处理 askUserQuestion / askMultipleQuestions 工具调用。
 *
 * <p>当前默认实现不再阻塞后端状态机，也不再创建 Redis pending session。
 * 工具结果会返回一个 type=questionnaire 的结构化 artifact，由前端识别后渲染“作答”按钮。
 * 用户提交后作为普通用户消息进入下一轮对话。
 *
 * <p>历史阻塞-恢复入口保留在 AnswerController/PendingSessionService 中，暂不作为默认链路。
 *
 * <ol>
 *   <li>解析问题列表</li>
 *   <li>构造 questionnaire artifact</li>
 *   <li>作为工具结果返回给 LLM，并由前端识别渲染</li>
 * </ol>
 */
@Slf4j
@Component
public class AskUserQuestionStrategy implements ToolExecutionStrategy {

    private static final String TOOL_NAME_ASK = "askUserQuestion";
    private static final String TOOL_NAME_ASK_MULTIPLE = "askMultipleQuestions";
    private static final String TOOL_NAME_QUESTIONNAIRE = "askQuestionnaire";

    private final AskUserQuestionParser askUserQuestionParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AskUserQuestionStrategy(AskUserQuestionParser askUserQuestionParser) {
        this.askUserQuestionParser = askUserQuestionParser;
    }

    @Override
    public boolean supports(ToolExecutionRequest request) {
        return isAskUserQuestionTool(request.name());
    }

    @Override
    public int getPriority() {
        // 最高优先级，Ask 类工具需要被优先转换成前端可识别的 artifact。
        return 5;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        QueryLoopState state = context.state();
        List<ToolExecutionRequest> requests = context.requests();

        // 找到第一个 AskUserQuestion 请求（挂起会话只需要处理第一个）
        ToolExecutionRequest askRequest = findAskUserQuestionRequest(requests);

        if (askRequest == null) {
            // 没有找到 AskUserQuestion 请求，返回错误
            return buildErrorResponse("未找到 askUserQuestion 请求");
        }

        log.info("[AskUserQuestionStrategy] 处理 AskUserQuestion 工具调用为 questionnaire artifact");

        try {
            // 1. 解析问题列表
            List<QuestionDto> questions = collectQuestions(requests);

            if (questions.isEmpty()) {
                log.warn("[AskUserQuestionStrategy] 问题解析失败，无有效问题");
                return ToolExecutionResult.builder()
                        .messages(resultMessagesForBatch(requests, askRequest, "问题解析失败，请检查参数格式"))
                        .contexts(requests)
                        .transition(ToolExecutionResult.TRANSITION_FAILED)
                        .discoveredTools(state.getDiscoveredTools())
                        .build();
            }

            if (isResumeExportConfirmationOnly(questions)) {
                log.info("[AskUserQuestionStrategy] 阻止导出二次确认，要求 LLM 交给工作台导出按钮");
                String message = "当前问题只是确认是否导出/下载简历。不要再调用 askUserQuestion/askQuestionnaire 追问导出确认，也不要把生成预览说成已经导出 PDF。简历生成或更新完成后，请返回明确的 type=resume artifact，让前端工作台自动打开预览、编辑和 AI 优化；PDF 导出由用户在工作台点击“导出 PDF”按钮触发。";
                return ToolExecutionResult.builder()
                        .messages(resultMessagesForBatch(requests, askRequest, message))
                        .contexts(requests)
                        .transition(ToolExecutionResult.TRANSITION_SUCCESS)
                        .discoveredTools(state.getDiscoveredTools())
                        .build();
            }

            String artifactJson = buildQuestionnaireArtifact(askRequest, questions);

            log.info("[AskUserQuestionStrategy] questionnaire artifact 已生成: tool={}, questionsCount={}",
                    askRequest.name(), questions.size());
            return ToolExecutionResult.builder()
                    .messages(resultMessagesForBatch(requests, askRequest, artifactJson))
                    .contexts(requests)
                    .transition(ToolExecutionResult.TRANSITION_ARTIFACT_READY)
                    .discoveredTools(state.getDiscoveredTools())
                    .build();

        } catch (Exception e) {
            log.error("[AskUserQuestionStrategy] AskUserQuestion 处理失败", e);
            return ToolExecutionResult.builder()
                    .messages(resultMessagesForBatch(requests, askRequest,
                            "AskUserQuestion 处理失败: " + e.getMessage()))
                    .contexts(requests)
                    .transition(ToolExecutionResult.TRANSITION_FAILED)
                    .discoveredTools(state.getDiscoveredTools())
                    .build();
        }
    }

    /**
     * 从请求列表中找到第一个 AskUserQuestion 请求
     */
    private ToolExecutionRequest findAskUserQuestionRequest(List<ToolExecutionRequest> requests) {
        for (ToolExecutionRequest req : requests) {
            if (isAskUserQuestionTool(req.name())) {
                return req;
            }
        }
        return null;
    }

    private boolean isAskUserQuestionTool(String toolName) {
        return TOOL_NAME_ASK.equals(toolName)
                || TOOL_NAME_ASK_MULTIPLE.equals(toolName)
                || TOOL_NAME_QUESTIONNAIRE.equals(toolName);
    }

    private List<QuestionDto> collectQuestions(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<QuestionDto> questions = new ArrayList<>();
        for (ToolExecutionRequest request : requests) {
            if (!isAskUserQuestionTool(request.name())) {
                continue;
            }
            questions.addAll(askUserQuestionParser.parse(request.arguments(), request.name()));
        }
        return questions;
    }

    private List<ToolExecutionResultMessage> resultMessagesForBatch(
            List<ToolExecutionRequest> requests,
            ToolExecutionRequest primaryAskRequest,
            String primaryResult) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<ToolExecutionResultMessage> messages = new ArrayList<>();
        boolean primaryResultUsed = false;
        for (ToolExecutionRequest request : requests) {
            String result;
            if (request == primaryAskRequest || (!primaryResultUsed && Objects.equals(request.id(), primaryAskRequest.id()))) {
                result = primaryResult;
                primaryResultUsed = true;
            } else if (isAskUserQuestionTool(request.name())) {
                result = "问题已合并到同一个前端问卷，请等待用户一次性补充。";
            } else {
                result = "本批次包含需要用户补充的信息，工具未执行；请等待用户回答后再继续。";
            }
            messages.add(ToolExecutionResultMessage.from(request, result));
        }
        return messages;
    }

    private String buildQuestionnaireArtifact(ToolExecutionRequest askRequest, List<QuestionDto> questions) throws JsonProcessingException {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("type", "questionnaire");
        artifact.put("questionnaireId", UUID.randomUUID().toString());
        artifact.put("title", resolveTitle(askRequest));
        artifact.put("sourceTool", askRequest.name());
        artifact.put("questions", questions);
        artifact.put("assistantInstruction",
                "This questionnaire has been delivered to the frontend as an artifact. "
                        + "Do not answer it yourself and do not call askUserQuestion/askMultipleQuestions/askQuestionnaire again for the same missing information. "
                        + "Reply briefly and tell the user to click the answer button. "
                        + "The user's answers will arrive later as a normal user message.");
        return objectMapper.writeValueAsString(artifact);
    }

    private String resolveTitle(ToolExecutionRequest askRequest) {
        if (TOOL_NAME_ASK.equals(askRequest.name())) {
            return "需要你补充";
        }

        try {
            com.fasterxml.jackson.databind.JsonNode args = objectMapper.readTree(askRequest.arguments());
            com.fasterxml.jackson.databind.JsonNode title = args.get("title");
            if (title != null && title.isTextual() && !title.asText().isBlank()) {
                return title.asText();
            }
        } catch (Exception ignored) {
            // 使用默认标题。
        }
        return TOOL_NAME_QUESTIONNAIRE.equals(askRequest.name()) ? "问卷" : "需要你补充";
    }

    private boolean isResumeExportConfirmationOnly(List<QuestionDto> questions) {
        if (questions == null || questions.isEmpty() || questions.size() > 2) {
            return false;
        }

        return questions.stream().allMatch(this::isResumeExportConfirmationQuestion);
    }

    private boolean isResumeExportConfirmationQuestion(QuestionDto question) {
        String text = normalizeText(question.getQuestionText() + " " + optionText(question));
        if (text.isBlank()) {
            return false;
        }

        boolean asksConfirmation = containsAny(text, "确认", "是否", "要不要", "需要", "开始", "现在", "点击", "下载", "导出");
        boolean aboutExport = containsAny(text, "导出", "下载", "文件", "pdf", "html", "简历");
        boolean asksForMissingResumeData = containsAny(text, "手机号", "手机", "电话", "邮箱", "email", "日期", "时间", "项目", "经历", "姓名", "学校", "公司", "岗位", "技能", "联系");

        return asksConfirmation && aboutExport && !asksForMissingResumeData;
    }

    private String optionText(QuestionDto question) {
        if (question.getOptions() == null || question.getOptions().isEmpty()) {
            return "";
        }

        return question.getOptions().stream()
                .map(option -> option.getDisplayText() + " " + option.getDescription())
                .collect(Collectors.joining(" "));
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase().replaceAll("\\s+", "");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建错误响应
     */
    private ToolExecutionResult buildErrorResponse(String errorMessage) {
        return ToolExecutionResult.builder()
                .transition(ToolExecutionResult.TRANSITION_FAILED)
                .build();
    }
}
