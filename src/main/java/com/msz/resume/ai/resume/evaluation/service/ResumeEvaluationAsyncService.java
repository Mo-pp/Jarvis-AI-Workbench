package com.msz.resume.ai.resume.evaluation.service;

import com.msz.resume.ai.file.dto.ParsedFile;
import com.msz.resume.ai.file.service.FileStorageService;
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
    private final FileStorageService fileStorageService;

    public ResumeEvaluationAsyncService(ResumeEvaluationService evaluationService,
                                        ResumeEvaluationJobService jobService,
                                        FileStorageService fileStorageService) {
        this.evaluationService = evaluationService;
        this.jobService = jobService;
        this.fileStorageService = fileStorageService;
    }

    @Async("resumeEvaluationExecutor")
    public void evaluateLater(String jobId, ResumeEvaluationRequest request) {
        jobService.markRunning(jobId);
        try {
            ResumeEvaluationRequest resolvedRequest = resolveSourceFileText(request);
            log.info("[ResumeEvaluationAsyncService] async evaluation started: jobId={}, withJd={}",
                    jobId, hasText(resolvedRequest.getJobDescription()));
            ResumeEvaluationBundle result = hasText(resolvedRequest.getJobDescription())
                    ? evaluationService.evaluateWithJdStrict(resolvedRequest)
                    : evaluationService.evaluateWithoutJdStrict(resolvedRequest);
            jobService.markSuccess(jobId, result);
            log.info("[ResumeEvaluationAsyncService] async evaluation completed: jobId={}", jobId);
        } catch (Exception e) {
            jobService.markFailed(jobId, e.getMessage());
            log.warn("[ResumeEvaluationAsyncService] async evaluation failed: jobId={}, error={}",
                    jobId, e.getMessage());
        }
    }

    private ResumeEvaluationRequest resolveSourceFileText(ResumeEvaluationRequest request) {
        ResumeEvaluationRequest normalized = request != null ? request : new ResumeEvaluationRequest();
        if (!hasText(normalized.getSourceFileId())) {
            return normalized;
        }
        ParsedFile parsedFile = fileStorageService.get(normalized.getSourceFileId())
                .orElseThrow(() -> new IllegalArgumentException("原始简历文件不存在或已过期: " + normalized.getSourceFileId()));
        if (!parsedFile.isSuccess()) {
            throw new IllegalArgumentException("原始简历文件解析失败: " + blankTo(parsedFile.getErrorMessage(), parsedFile.getFileName()));
        }
        if (!"document".equals(parsedFile.getFileKind())) {
            throw new IllegalArgumentException("原始简历文件必须是已解析文档: " + blankTo(parsedFile.getFileName(), normalized.getSourceFileId()));
        }
        if (!hasText(parsedFile.getContent())) {
            throw new IllegalArgumentException("原始简历文件解析文本为空: " + blankTo(parsedFile.getFileName(), normalized.getSourceFileId()));
        }
        return ResumeEvaluationRequest.builder()
                .sourceFileId(normalized.getSourceFileId())
                .originalResumeText(parsedFile.getContent())
                .generatedResume(normalized.getGeneratedResume())
                .jobDescription(normalized.getJobDescription())
                .targetPosition(normalized.getTargetPosition())
                .build();
    }

    private String blankTo(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
