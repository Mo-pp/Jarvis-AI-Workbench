package com.msz.resume.ai.resume.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.dto.ResumeVO;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.entity.ResumeEvaluationJob;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationAsyncService;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationJobService;
import com.msz.resume.ai.tool.CoreTool;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@CoreTool
@Component
public class ResumeEvaluationTool {

    private final ResumeEvaluationJobService jobService;
    private final ResumeEvaluationAsyncService asyncService;
    private final ObjectMapper objectMapper;

    public ResumeEvaluationTool(ResumeEvaluationJobService jobService,
                                ResumeEvaluationAsyncService asyncService,
                                ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.asyncService = asyncService;
        this.objectMapper = objectMapper;
    }

    @Tool("""
            Evaluate resume quality and optional JD matching by calling the dedicated resume scoring sub-agent.
            Use this after producing a resume or optimize_result artifact when resume scoring is needed.
            Inputs: originalResumeText is the extracted raw resume text, generatedResume is the Jarvis preview ResumeVO,
            jobDescription is optional, targetPosition is optional.
            If jobDescription is blank or not explicitly available, this tool MUST NOT produce JD match scoring.
            If jobDescription is present, quality scoring includes JD relevance with fixed weight 45 and also returns jdMatch.
            Return value is a pending evaluation job JSON. The real scoring runs in the background.
            """)
    public String evaluateResume(
            @P(value = "Original resume text extracted from the user input/file/image. Do not invent it.", required = false)
            String originalResumeText,
            @P(value = "Jarvis generated/preview resume object. Use this when available.", required = false)
            ResumeVO generatedResume,
            @P(value = "Job description text. Leave blank unless the user provided JD or clearly mentioned JD-related requirements.", required = false)
            String jobDescription,
            @P(value = "Target position, if known.", required = false)
            String targetPosition
    ) {
        try {
            ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                    .originalResumeText(originalResumeText)
                    .generatedResume(generatedResume)
                    .jobDescription(jobDescription)
                    .targetPosition(targetPosition)
                    .build();

            ResumeEvaluationJob job = jobService.createPendingJob(
                    ToolRuntimeContext.getSessionId(),
                    ToolRuntimeContext.getRunId(),
                    request
            );
            try {
                asyncService.evaluateLater(job.getJobId(), request);
            } catch (Exception e) {
                jobService.markFailed(job.getJobId(), "schedule evaluation job failed: " + e.getMessage());
                throw e;
            }

            return objectMapper.writeValueAsString(Map.of(
                    "type", "resume_evaluation_pending",
                    "payload", Map.of(
                            "jobId", job.getJobId(),
                            "status", "pending",
                            "statusUrl", "/api/resume/evaluation/status?jobId=" + job.getJobId()
                    )
            ));
        } catch (Exception e) {
            log.warn("[ResumeEvaluationTool] 评分失败: {}", e.getMessage());
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "type", "error",
                        "message", e.getMessage()
                ));
            } catch (Exception ignored) {
                return "{\"type\":\"error\",\"message\":\"resume evaluation failed\"}";
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
