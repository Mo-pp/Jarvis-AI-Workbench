package com.msz.resume.ai.resume.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.msz.resume.ai.resume.dto.ResumeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResumeEvaluationRequest {

    private String jobDescription;

    private String targetPosition;

    @Builder.Default
    private List<CandidateResume> candidates = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CandidateResume {
        private String candidateId;
        private String candidateName;
        private String originalResumeText;
        private ResumeVO generatedResume;
    }
}
