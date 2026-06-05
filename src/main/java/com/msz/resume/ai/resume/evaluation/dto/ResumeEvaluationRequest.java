package com.msz.resume.ai.resume.evaluation.dto;

import com.msz.resume.ai.resume.dto.ResumeVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeEvaluationRequest {

    private String originalResumeText;

    private ResumeVO generatedResume;

    private String jobDescription;

    private String targetPosition;
}
