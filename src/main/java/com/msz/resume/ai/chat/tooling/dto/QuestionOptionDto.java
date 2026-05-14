package com.msz.resume.ai.chat.tooling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 问题选项 DTO
 *
 * <p>表示多选问题中的单个选项。
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li><b>optionId</b>: 选项唯一标识，用于用户回答时标识选择的选项</li>
 *   <li><b>displayText</b>: 选项显示文本，前端渲染时显示给用户的选项内容</li>
 *   <li><b>description</b>: 选项描述（可选），用于提供额外的选项说明</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * QuestionOptionDto option = QuestionOptionDto.builder()
 *     .optionId("opt_1")
 *     .displayText("方案A")
 *     .description("完整功能体验")
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 选项唯一标识
     * 用于用户回答时标识选择的选项
     */
    private String optionId;

    /**
     * 选项显示文本
     * 前端渲染时显示给用户的选项内容
     */
    private String displayText;

    /**
     * 选项描述（可选）
     * 用于提供额外的选项说明
     */
    private String description;
}
