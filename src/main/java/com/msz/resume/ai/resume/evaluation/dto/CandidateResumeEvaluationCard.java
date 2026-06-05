package com.msz.resume.ai.resume.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidateResumeEvaluationCard {

    private String candidateId;

    private String candidateName;

    private Integer score;

    private String summary;

    private ResumeEvaluationBundle evaluation;
}
