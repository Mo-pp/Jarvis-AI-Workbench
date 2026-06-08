package com.msz.resume.ai.resume.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.entity.ResumeEvaluationJob;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationAsyncService;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationJobService;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeEvaluationToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        ToolRuntimeContext.clear();
    }

    @Test
    @DisplayName("evaluateResume 只返回 pending job 并调度后台评分")
    void evaluateResumeShouldReturnPendingWithoutWaitingForScore() throws Exception {
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationAsyncService asyncService = mock(ResumeEvaluationAsyncService.class);
        ResumeEvaluationTool tool = new ResumeEvaluationTool(jobService, asyncService, objectMapper);

        ResumeEvaluationJob job = new ResumeEvaluationJob();
        job.setJobId("job-123");
        when(jobService.createPendingJob(eq("session-1"), eq("run-1"), any())).thenReturn(job);
        ToolRuntimeContext.setSessionId("session-1");
        ToolRuntimeContext.setRunId("run-1");

        String raw = tool.evaluateResume("file-123", "原始简历", null, "", "Java 后端");
        JsonNode node = objectMapper.readTree(raw);

        assertEquals("resume_evaluation_pending", node.path("type").asText());
        assertEquals("job-123", node.path("payload").path("jobId").asText());
        assertEquals("pending", node.path("payload").path("status").asText());
        assertEquals("/api/resume/evaluation/status?jobId=job-123",
                node.path("payload").path("statusUrl").asText());
        ArgumentCaptor<ResumeEvaluationRequest> requestCaptor = ArgumentCaptor.forClass(ResumeEvaluationRequest.class);
        verify(jobService).createPendingJob(eq("session-1"), eq("run-1"), requestCaptor.capture());
        assertEquals("file-123", requestCaptor.getValue().getSourceFileId());
        assertEquals("原始简历", requestCaptor.getValue().getOriginalResumeText());
        verify(asyncService).evaluateLater(eq("job-123"), requestCaptor.capture());
        assertEquals("file-123", requestCaptor.getValue().getSourceFileId());
    }
}
