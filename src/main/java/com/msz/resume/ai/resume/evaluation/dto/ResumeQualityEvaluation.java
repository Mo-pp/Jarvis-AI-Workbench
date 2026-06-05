package com.msz.resume.ai.resume.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeQualityEvaluation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer score;

    private String summary;

    /**
     * 带 JD 的质量评分中 JD 相关性权重固定为 45；无 JD 时为空。
     */
    private Integer jdWeight;

    @Builder.Default
    private Map<String, Integer> dimensionScores = new LinkedHashMap<>();

    @Builder.Default
    private List<String> strengths = List.of();

    @Builder.Default
    private List<String> issues = List.of();

    @Builder.Default
    private List<String> suggestions = List.of();
}
