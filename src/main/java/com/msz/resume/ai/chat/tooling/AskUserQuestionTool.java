package com.msz.resume.ai.chat.tooling;

import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Core tool for asking the user for missing information.
 *
 * <p>The method body should not be executed directly. ExecuteToolNode intercepts
 * these tool calls and returns a questionnaire artifact for the frontend.
 */
@Slf4j
@CoreTool
@Component
public class AskUserQuestionTool {

    @Tool("""
            Ask the user a question by creating a frontend questionnaire artifact.
            Supported questionType values: single, multiple, text, single_or_text, multiple_or_text.
            For every single or multiple choice question, always provide an Other/free-form path by
            using single_or_text or multiple_or_text, or by setting allowCustomInput=true.
            Use this only when you need missing information before continuing. The frontend will show
            an answer button; the user's answer will arrive later as a normal user message. Do not
            assume the answer in the same turn.
            """)
    public String askUserQuestion(
            @P("Question text shown to the user.") String questionText,
            @P("Options as a JSON array, for example [{\"displayText\":\"Option A\"}]. Omit for text questions. Do not add an explicit Other option here; use allowCustomInput=true or *_or_text instead.") String options,
            @P("Question type: single, multiple, text, single_or_text, multiple_or_text. Prefer single_or_text/multiple_or_text for choice questions so the user always has Other.") String questionType,
            @P("Whether free-form custom input is allowed. Set true for every single or multiple choice question.") Boolean allowCustomInput
    ) {
        log.warn("[AskUserQuestion] Tool method was invoked directly; this should be intercepted by ExecuteToolNode");
        return "AskUserQuestion tool calls should be intercepted by ExecuteToolNode";
    }

    @Tool("""
            Ask the user multiple questions by creating a frontend questionnaire artifact.
            For every single or multiple choice question in the questions JSON, always provide an
            Other/free-form path by using single_or_text/multiple_or_text or allowCustomInput=true.
            Use this when several pieces of information must be collected at once. The frontend will
            show an answer button; the user's answers will arrive later as a normal user message.
            """)
    public String askMultipleQuestions(
            @P("Questions as a JSON array. Each item includes questionText, questionType, options, allowCustomInput, etc. Choice questions must support Other/free-form input.") String questions,
            @P("Optional title.") String title
    ) {
        log.warn("[AskUserQuestion] Tool method was invoked directly; this should be intercepted by ExecuteToolNode");
        return "AskUserQuestion tool calls should be intercepted by ExecuteToolNode";
    }

    @Tool("""
            Show the user a single-page questionnaire as a frontend artifact.
            Use this when you need to collect several related fields at once, especially for resume generation.
            The questions parameter must be a JSON array. Each item supports questionText, questionType,
            options, allowCustomInput, customInputPlaceholder, required, and defaultValue.
            Supported questionType values: single, multiple, text, single_or_text, multiple_or_text, confirmation.
            Prefer concise questionnaires with clear labels and no duplicate questions.
            After calling this, briefly tell the user to click the answer button. Do not continue as if
            the answers were already available.
            """)
    public String askQuestionnaire(
            @P("Questionnaire title shown to the user, for example \"简历基础信息\".") String title,
            @P("Questions as a JSON array. Each item includes questionText, questionType, options, allowCustomInput, required, etc.") String questions
    ) {
        log.warn("[AskUserQuestion] Tool method was invoked directly; this should be intercepted by ExecuteToolNode");
        return "AskUserQuestion tool calls should be intercepted by ExecuteToolNode";
    }
}
