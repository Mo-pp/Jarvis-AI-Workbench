package com.msz.resume.ai.resume.evaluation.api;

import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationJobStatusResponse;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationJobService;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeEvaluationControllerTest {

    @Test
    @DisplayName("status endpoint 按 jobId 返回 pending")
    void statusShouldReturnPendingByJobId() throws Exception {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationController controller = new ResumeEvaluationController(evaluationService, jobService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(jobService.findStatus("job-1", null)).thenReturn(Optional.of(
                ResumeEvaluationJobStatusResponse.builder()
                        .jobId("job-1")
                        .status("pending")
                        .build()
        ));

        mockMvc.perform(get("/api/resume/evaluation/status").param("jobId", "job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.jobId").value("job-1"))
                .andExpect(jsonPath("$.data.status").value("pending"));
    }

    @Test
    @DisplayName("status endpoint 按 sessionId 返回最新 success")
    void statusShouldReturnLatestBySessionId() throws Exception {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationController controller = new ResumeEvaluationController(evaluationService, jobService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(jobService.findStatus(null, "session-1")).thenReturn(Optional.of(
                ResumeEvaluationJobStatusResponse.builder()
                        .jobId("job-2")
                        .sessionId("session-1")
                        .status("success")
                        .build()
        ));

        mockMvc.perform(get("/api/resume/evaluation/status").param("sessionId", "session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.jobId").value("job-2"))
                .andExpect(jsonPath("$.data.status").value("success"));
    }

    @Test
    @DisplayName("status endpoint 缺少 jobId/sessionId 时返回业务错误")
    void statusShouldRejectMissingIdentifiers() throws Exception {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationController controller = new ResumeEvaluationController(evaluationService, jobService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/resume/evaluation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("status endpoint 找不到任务时返回业务错误")
    void statusShouldReturnErrorWhenJobMissing() throws Exception {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        ResumeEvaluationController controller = new ResumeEvaluationController(evaluationService, jobService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(jobService.findStatus("missing", null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/resume/evaluation/status").param("jobId", "missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
