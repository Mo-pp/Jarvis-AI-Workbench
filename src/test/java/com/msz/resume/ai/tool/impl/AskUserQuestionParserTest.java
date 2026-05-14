package com.msz.resume.ai.chat.tooling;

import com.msz.resume.ai.chat.tooling.dto.QuestionDto;
import com.msz.resume.ai.chat.tooling.dto.QuestionOptionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AskUserQuestionParserTest {

    private final AskUserQuestionParser parser = new AskUserQuestionParser();

    @Test
    @DisplayName("问卷解析会保留字符串数组选项的真实展示文本")
    void parseQuestionnaireKeepsStringArrayOptionText() {
        List<QuestionDto> questions = parser.parse("""
                {
                  "title": "Java 后端面试设置",
                  "questions": "[{\\"questionText\\":\\"请选择想要的 Java 后端面试模式\\",\\"questionType\\":\\"single_or_text\\",\\"options\\":[\\"快速刷题（直接答题）\\",\\"模拟真实面试（我逐题追问）\\",\\"专项训练（Java / Spring / MySQL / Redis / 并发 / JVM）\\"]}]"
                }
                """, "askQuestionnaire");

        assertEquals(1, questions.size());
        List<QuestionOptionDto> options = questions.get(0).getOptions();
        assertEquals(3, options.size());
        assertEquals("opt_0", options.get(0).getOptionId());
        assertEquals("快速刷题（直接答题）", options.get(0).getDisplayText());
        assertEquals("模拟真实面试（我逐题追问）", options.get(1).getDisplayText());
        assertEquals("专项训练（Java / Spring / MySQL / Redis / 并发 / JVM）", options.get(2).getDisplayText());
    }

    @Test
    @DisplayName("问卷解析会兼容对象选项的展示文本别名")
    void parseQuestionnaireKeepsObjectOptionTextAliases() {
        List<QuestionDto> questions = parser.parse("""
                {
                  "questions": [
                    {
                      "questionText": "请选择级别",
                      "questionType": "single",
                      "options": [
                        {"id": "junior", "label": "初级"},
                        {"value": "senior", "optionText": "高级", "description": "5 年以上"}
                      ]
                    }
                  ]
                }
                """, "askQuestionnaire");

        List<QuestionOptionDto> options = questions.get(0).getOptions();
        assertEquals("junior", options.get(0).getOptionId());
        assertEquals("初级", options.get(0).getDisplayText());
        assertEquals("senior", options.get(1).getOptionId());
        assertEquals("高级", options.get(1).getDisplayText());
        assertEquals("5 年以上", options.get(1).getDescription());
    }
}
