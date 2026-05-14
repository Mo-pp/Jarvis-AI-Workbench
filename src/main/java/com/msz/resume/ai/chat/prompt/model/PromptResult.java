package com.msz.resume.ai.chat.prompt.model;

/**
 * 系统提示词构建结果
 */
public record PromptResult(
        String systemPrompt,
        String staticSectionContent,
        String dynamicSectionContent,
        int tokenEstimate
) {

    /**
     * 静态/动态部分分隔符
     */
    public static final String BOUNDARY = "__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__";

    /**
     * 创建空结果
     */
    public static PromptResult empty() {
        return new PromptResult("", "", "", 0);
    }
}
