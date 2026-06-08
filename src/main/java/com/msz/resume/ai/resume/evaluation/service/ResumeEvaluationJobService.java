package com.msz.resume.ai.resume.evaluation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationJobStatusResponse;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.entity.ResumeEvaluationJob;
import com.msz.resume.ai.resume.evaluation.mapper.ResumeEvaluationJobMapper;
import com.msz.resume.ai.resume.evaluation.model.ResumeEvaluationJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ResumeEvaluationJobService {

    private static final int MAX_ERROR_MESSAGE_CHARS = 4000;

    private final ResumeEvaluationJobMapper mapper;
    private final ObjectMapper objectMapper;

    public ResumeEvaluationJobService(ResumeEvaluationJobMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ResumeEvaluationJob createPendingJob(String sessionId, String runId, ResumeEvaluationRequest request) {
        ResumeEvaluationJob job = new ResumeEvaluationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setSessionId(blankToNull(sessionId));
        job.setRunId(blankToNull(runId));
        job.setStatus(ResumeEvaluationJobStatus.pending.name());
        job.setSourceFileId(request != null ? blankToNull(request.getSourceFileId()) : null);
        job.setOriginalResumeText(request != null ? request.getOriginalResumeText() : null);
        job.setGeneratedResumeJson(serializeGeneratedResume(request));
        job.setJobDescription(request != null ? request.getJobDescription() : null);
        job.setTargetPosition(request != null ? request.getTargetPosition() : null);
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        mapper.insert(job);
        log.info("[ResumeEvaluationJobService] created evaluation job: jobId={}, sessionId={}, runId={}",
                job.getJobId(), job.getSessionId(), job.getRunId());
        return job;
    }

    public void markRunning(String jobId) {
        if (!hasText(jobId)) {
            return;
        }
        mapper.markRunning(jobId);
        log.info("[ResumeEvaluationJobService] evaluation job running: jobId={}", jobId);
    }

    public void markSuccess(String jobId, ResumeEvaluationBundle result) {
        if (!hasText(jobId)) {
            return;
        }
        try {
            mapper.markSuccess(jobId, objectMapper.writeValueAsString(result));
            log.info("[ResumeEvaluationJobService] evaluation job success: jobId={}", jobId);
        } catch (JsonProcessingException e) {
            markFailed(jobId, "serialize evaluation result failed: " + e.getMessage());
        }
    }

    public void markFailed(String jobId, String errorMessage) {
        if (!hasText(jobId)) {
            return;
        }
        String sanitized = sanitizeErrorMessage(errorMessage);
        mapper.markFailed(jobId, sanitized);
        log.warn("[ResumeEvaluationJobService] evaluation job failed: jobId={}, error={}", jobId, sanitized);
    }

    public Optional<ResumeEvaluationJobStatusResponse> findStatus(String jobId, String sessionId) {
        ResumeEvaluationJob job = null;
        if (hasText(jobId)) {
            job = mapper.selectByJobId(jobId);
        } else if (hasText(sessionId)) {
            job = mapper.selectLatestBySessionId(sessionId);
        }
        return Optional.ofNullable(job).map(this::toStatusResponse);
    }

    private ResumeEvaluationJobStatusResponse toStatusResponse(ResumeEvaluationJob job) {
        ResumeEvaluationBundle result = null;
        if (hasText(job.getResultJson())) {
            try {
                result = objectMapper.readValue(job.getResultJson(), ResumeEvaluationBundle.class);
            } catch (Exception e) {
                log.warn("[ResumeEvaluationJobService] parse evaluation result failed: jobId={}, error={}",
                        job.getJobId(), e.getMessage());
            }
        }
        return ResumeEvaluationJobStatusResponse.builder()
                .jobId(job.getJobId())
                .sessionId(job.getSessionId())
                .runId(job.getRunId())
                .status(job.getStatus())
                .result(result)
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    private String serializeGeneratedResume(ResumeEvaluationRequest request) {
        if (request == null || request.getGeneratedResume() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request.getGeneratedResume());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("generatedResume JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    private String sanitizeErrorMessage(String errorMessage) {
        String value = hasText(errorMessage) ? errorMessage.trim() : "resume evaluation failed";
        if (value.length() <= MAX_ERROR_MESSAGE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_CHARS) + "...";
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
