package com.msz.resume.ai.resume.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeEvaluationBundle implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 兼容/聚合字段：优先等于 Jarvis 生成预览评分，没有预览时等于原始简历评分。
     */
    private ResumeQualityEvaluation quality;

    private ResumeQualityEvaluation originalResume;

    private ResumeQualityEvaluation generatedResume;

    private JdMatchEvaluation jdMatch;

    private Boolean hasJd;

    private String targetPosition;
}
