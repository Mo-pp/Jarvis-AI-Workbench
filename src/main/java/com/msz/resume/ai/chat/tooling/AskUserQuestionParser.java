package com.msz.resume.ai.chat.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import com.msz.resume.ai.chat.tooling.dto.QuestionOptionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AskUserQuestion 工具参数解析器
 *
 * 负责将 LLM 返回的 JSON 参数解析为 {@link QuestionDto} 列表。
 * 支持 askUserQuestion（单问题）、askMultipleQuestions（多问题）和 askQuestionnaire（问卷）调用格式。
 */
@Slf4j
@Component
public class AskUserQuestionParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析 AskUserQuestion 工具参数
     *
     * @param arguments JSON 格式的参数字符串
     * @param toolName  工具名称（askUserQuestion、askMultipleQuestions 或 askQuestionnaire）
     * @return 问题列表
     */
    public List<QuestionDto> parse(String arguments, String toolName) {
        List<QuestionDto> questions = new ArrayList<>();

        try {
            JsonNode args = objectMapper.readTree(arguments);

            if ("askMultipleQuestions".equals(toolName) || "askQuestionnaire".equals(toolName)) {
                JsonNode questionsNode = args.get("questions");

                // LLM 有时会把数组序列化为字符串传递，需要兼容两种格式
                if (questionsNode != null && questionsNode.isTextual()) {
                    try {
                        questionsNode = objectMapper.readTree(questionsNode.asText());
                    } catch (Exception e) {
                        log.warn("[AskUserQuestionParser] 解析 questions 字符串失败: {}", e.getMessage());
                    }
                }

                if (questionsNode != null && questionsNode.isArray()) {
                    for (JsonNode qNode : questionsNode) {
                        QuestionDto question = parseQuestionNode(qNode);
                        if (question != null) {
                            questions.add(question);
                        }
                    }
                }
            } else {
                QuestionDto question = parseSingleQuestionArgs(args);
                if (question != null) {
                    questions.add(question);
                }
            }

        } catch (Exception e) {
            log.warn("[AskUserQuestionParser] 解析参数失败: {}", e.getMessage());
        }

        return questions;
    }

    private QuestionDto parseSingleQuestionArgs(JsonNode args) {
        String questionText = firstText(args, "questionText", "label", "title");
        if (questionText == null || questionText.isBlank()) {
            return null;
        }

        String questionType = normalizeQuestionType(firstText(args, "questionType", "type"));
        boolean allowCustomInput = shouldAllowCustomInputByDefault(questionType) ||
                (args.has("allowCustomInput") && args.get("allowCustomInput").asBoolean());

        List<QuestionOptionDto> options = new ArrayList<>();
        if (args.has("options")) {
            options = parseOptionsNode(args.get("options"));
        }

        return QuestionDto.builder()
                .questionId(UUID.randomUUID().toString())
                .questionText(questionText)
                .questionType(questionType)
                .allowCustomInput(allowCustomInput)
                .options(options.isEmpty() ? null : options)
                .customInputPlaceholder(firstText(args, "customInputPlaceholder", "placeholder"))
                .required(args.has("required") ? args.get("required").asBoolean() : true)
                .defaultValue(firstText(args, "defaultValue"))
                .build();
    }

    private QuestionDto parseQuestionNode(JsonNode qNode) {
        String questionText = firstText(qNode, "questionText", "label", "title");
        if (questionText == null || questionText.isBlank()) {
            return null;
        }

        String questionId = qNode.has("questionId") ? qNode.get("questionId").asText() : UUID.randomUUID().toString();
        String questionType = normalizeQuestionType(firstText(qNode, "questionType", "type"));
        boolean allowCustomInput = shouldAllowCustomInputByDefault(questionType) ||
                (qNode.has("allowCustomInput") && qNode.get("allowCustomInput").asBoolean());

        List<QuestionOptionDto> options = new ArrayList<>();
        if (qNode.has("options")) {
            options = parseOptionsNode(qNode.get("options"));
        }

        return QuestionDto.builder()
                .questionId(questionId)
                .questionText(questionText)
                .questionType(questionType)
                .allowCustomInput(allowCustomInput)
                .options(options.isEmpty() ? null : options)
                .customInputPlaceholder(firstText(qNode, "customInputPlaceholder", "placeholder"))
                .required(qNode.has("required") ? qNode.get("required").asBoolean() : true)
                .defaultValue(firstText(qNode, "defaultValue"))
                .build();
    }

    private List<QuestionOptionDto> parseOptionsNode(JsonNode optionsNode) {
        List<QuestionOptionDto> options = new ArrayList<>();

        if (optionsNode == null) {
            return options;
        }

        JsonNode actualArray = optionsNode;
        if (optionsNode.isTextual()) {
            try {
                actualArray = objectMapper.readTree(optionsNode.asText());
            } catch (Exception e) {
                log.warn("[AskUserQuestionParser] 解析 options 字符串失败: {}", e.getMessage());
                return options;
            }
        }

        if (!actualArray.isArray()) {
            return options;
        }

        int index = 0;
        for (JsonNode optNode : actualArray) {
            String optionId = firstText(optNode, "optionId", "id", "value");
            String displayText = optionDisplayText(optNode);
            String description = firstText(optNode, "description", "desc");
            if (optionId == null || optionId.isBlank()) {
                optionId = "opt_" + index;
            }
            if (displayText == null || displayText.isBlank()) {
                displayText = "选项" + (index + 1);
            }

            options.add(QuestionOptionDto.builder()
                    .optionId(optionId)
                    .displayText(displayText)
                    .description(description)
                    .build());
            index++;
        }

        return options;
    }

    private String optionDisplayText(JsonNode optNode) {
        if (optNode == null || optNode.isNull()) {
            return null;
        }
        if (optNode.isValueNode()) {
            String text = optNode.asText();
            return text == null || text.isBlank() ? null : text;
        }
        return firstText(optNode, "displayText", "optionText", "text", "label", "name", "value");
    }

    private String normalizeQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "single";
        }

        return switch (questionType.trim().toLowerCase()) {
            case "single_choice" -> "single";
            case "multiple_choice" -> "multiple";
            case "text_input" -> "text";
            case "confirm" -> "confirmation";
            default -> questionType.trim().toLowerCase();
        };
    }

    private boolean shouldAllowCustomInputByDefault(String questionType) {
        return "single".equals(questionType)
                || "multiple".equals(questionType)
                || "single_or_text".equals(questionType)
                || "multiple_or_text".equals(questionType);
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }

        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }
}
