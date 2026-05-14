package com.msz.resume.ai.chat.tooling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户单个问题的答案 DTO
 *
 * <p>表示用户对单个问题的回答，支持多选、自定义输入和备注。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAnswerDto {

    /**
     * 问题ID
     * 对应 QuestionDto.questionId
     */
    private String questionId;

    /**
     * 用户选择的选项ID列表
     * 单选时只有一个元素，多选时可能有多个
     */
    private List<String> selectedOptionIds;

    /**
     * 用户自定义输入内容
     * 当 allowCustomInput=true 且用户选择自定义输入时使用
     */
    private String customInput;

    /**
     * 用户备注（可选）
     * 用户对选择或输入的额外说明
     */
    private String notes;

    /**
     * 是否跳过
     * 当问题非必填时，用户可以选择跳过
     */
    @Builder.Default
    private Boolean skipped = false;
}
