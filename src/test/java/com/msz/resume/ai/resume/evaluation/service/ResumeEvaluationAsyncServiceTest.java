package com.msz.resume.ai.resume.evaluation.service;

import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.ResumeQualityEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeEvaluationAsyncServiceTest {

    @Test
    @DisplayName("后台无 JD 评分成功时写入 success/result")
    void evaluateLaterShouldMarkSuccessWithoutJd() {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService);

        ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                .originalResumeText("Java 后端")
                .build();
        ResumeEvaluationBundle bundle = ResumeEvaluationBundle.builder()
                .quality(ResumeQualityEvaluation.builder().score(88).summary("质量较好").build())
                .hasJd(false)
                .build();
        when(evaluationService.evaluateWithoutJdStrict(request)).thenReturn(bundle);

        asyncService.evaluateLater("job-1", request);

        verify(jobService).markRunning("job-1");
        verify(evaluationService).evaluateWithoutJdStrict(request);
        verify(jobService).markSuccess("job-1", bundle);
    }

    @Test
    @DisplayName("后台有 JD 评分成功时调用 evaluateWithJd")
    void evaluateLaterShouldUseWithJdWhenJobDescriptionExists() {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService);

        ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                .jobDescription("需要 Spring Boot")
                .build();
        ResumeEvaluationBundle bundle = ResumeEvaluationBundle.builder().hasJd(true).build();
        when(evaluationService.evaluateWithJdStrict(request)).thenReturn(bundle);

        asyncService.evaluateLater("job-2", request);

        verify(jobService).markRunning("job-2");
        verify(evaluationService).evaluateWithJdStrict(request);
        verify(jobService).markSuccess("job-2", bundle);
    }

    @Test
    @DisplayName("后台评分异常时写入 failed/errorMessage")
    void evaluateLaterShouldMarkFailedWhenEvaluationThrows() {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService);

        ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                .originalResumeText("Java 后端")
                .build();
        when(evaluationService.evaluateWithoutJdStrict(request)).thenThrow(new IllegalStateException("LLM timeout"));

        asyncService.evaluateLater("job-3", request);

        verify(jobService).markRunning("job-3");
        verify(jobService).markFailed("job-3", "LLM timeout");
    }
}
