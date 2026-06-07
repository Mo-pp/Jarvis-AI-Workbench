package com.msz.resume.ai.resume.evaluation.service;

import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ResumeEvaluationAsyncService {

    private final ResumeEvaluationService evaluationService;
    private final ResumeEvaluationJobService jobService;

    public ResumeEvaluationAsyncService(ResumeEvaluationService evaluationService,
                                        ResumeEvaluationJobService jobService) {
        this.evaluationService = evaluationService;
        this.jobService = jobService;
    }

    @Async("resumeEvaluationExecutor")
    public void evaluateLater(String jobId, ResumeEvaluationRequest request) {
        jobService.markRunning(jobId);
        try {
            log.info("[ResumeEvaluationAsyncService] async evaluation started: jobId={}, withJd={}",
                    jobId, hasText(request != null ? request.getJobDescription() : null));
            ResumeEvaluationBundle result = hasText(request != null ? request.getJobDescription() : null)
                    ? evaluationService.evaluateWithJdStrict(request)
                    : evaluationService.evaluateWithoutJdStrict(request);
            jobService.markSuccess(jobId, result);
            log.info("[ResumeEvaluationAsyncService] async evaluation completed: jobId={}", jobId);
        } catch (Exception e) {
            jobService.markFailed(jobId, e.getMessage());
            log.warn("[ResumeEvaluationAsyncService] async evaluation failed: jobId={}, error={}",
                    jobId, e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
