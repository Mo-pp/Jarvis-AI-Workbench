package com.msz.resume.ai.chat.tooling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 问题 DTO
 *
 * <p>表示单个问题的完整定义，支持单选、多选和用户自定义输入。
 *
 * <h2>问题类型</h2>
 * <ul>
 *   <li><b>single</b>: 单选，从选项中选择一个</li>
 *   <li><b>multiple</b>: 多选，从选项中选择多个</li>
 *   <li><b>text</b>: 文本输入，用户自定义输入</li>
 *   <li><b>single_or_text</b>: 单选或自定义输入</li>
 *   <li><b>multiple_or_text</b>: 多选或自定义输入</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * QuestionDto question = QuestionDto.builder()
 *     .questionId("q1")
 *     .questionText("请选择您熟悉的编程语言")
 *     .questionType("multiple_or_text")
 *     .options(List.of(
 *         QuestionOptionDto.builder().optionId("java").displayText("Java").build(),
 *         QuestionOptionDto.builder().optionId("python").displayText("Python").build()
 *     ))
 *     .allowCustomInput(true)
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 问题唯一标识
     * 用于用户回答时关联到具体问题
     */
    private String questionId;

    /**
     * 问题文本
     * 向用户展示的问题内容
     */
    private String questionText;

    /**
     * 问题类型
     * <ul>
     *   <li>single: 单选</li>
     *   <li>multiple: 多选</li>
     *   <li>text: 文本输入</li>
     *   <li>single_or_text: 单选或自定义输入</li>
     *   <li>multiple_or_text: 多选或自定义输入</li>
     * </ul>
     */
    @Builder.Default
    private String questionType = "single";

    /**
     * 选项列表
     * 单选/多选问题必填，文本问题可为空
     */
    private List<QuestionOptionDto> options;

    /**
     * 是否允许用户自定义输入
     * 为 true 时，用户可以选择输入自定义内容而非选择选项
     */
    @Builder.Default
    private Boolean allowCustomInput = false;

    /**
     * 自定义输入占位符（可选）
     * 当 allowCustomInput=true 时，显示在输入框中的占位符文本
     */
    private String customInputPlaceholder;

    /**
     * 是否必填
     */
    @Builder.Default
    private Boolean required = true;

    /**
     * 默认值（可选）
     * 用户未回答时使用的默认值
     */
    private String defaultValue;
}
