package com.msz.resume.ai.resume.evaluation.service;

import com.msz.resume.ai.file.dto.ParsedFile;
import com.msz.resume.ai.file.service.FileStorageService;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.ResumeQualityEvaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeEvaluationAsyncServiceTest {

    @Test
    @DisplayName("后台无 JD 评分成功时写入 success/result")
    void evaluateLaterShouldMarkSuccessWithoutJd() {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService, fileStorageService);

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
        FileStorageService fileStorageService = mock(FileStorageService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService, fileStorageService);

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
        FileStorageService fileStorageService = mock(FileStorageService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService, fileStorageService);

        ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                .originalResumeText("Java 后端")
                .build();
        when(evaluationService.evaluateWithoutJdStrict(request)).thenThrow(new IllegalStateException("LLM timeout"));

        asyncService.evaluateLater("job-3", request);

        verify(jobService).markRunning("job-3");
        verify(jobService).markFailed("job-3", "LLM timeout");
    }

    @Test
    @DisplayName("后台评分存在 sourceFileId 时从 Redis 取解析文本")
    void evaluateLaterShouldResolveSourceFileTextFromRedis() {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService, fileStorageService);

        ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                .sourceFileId("file-1")
                .originalResumeText("不应使用这段旧文本")
                .build();
        ParsedFile parsedFile = ParsedFile.builder()
                .fileId("file-1")
                .fileName("resume.pdf")
                .fileType("pdf")
                .fileKind("document")
                .content("Redis 中的原始简历文本")
                .success(true)
                .build();
        ResumeEvaluationBundle bundle = ResumeEvaluationBundle.builder().hasJd(false).build();
        when(fileStorageService.get("file-1")).thenReturn(Optional.of(parsedFile));
        when(evaluationService.evaluateWithoutJdStrict(argThat(resolved ->
                "file-1".equals(resolved.getSourceFileId())
                        && "Redis 中的原始简历文本".equals(resolved.getOriginalResumeText())
        ))).thenReturn(bundle);

        asyncService.evaluateLater("job-4", request);

        verify(jobService).markRunning("job-4");
        verify(evaluationService).evaluateWithoutJdStrict(argThat(resolved ->
                "Redis 中的原始简历文本".equals(resolved.getOriginalResumeText())
        ));
        verify(jobService).markSuccess("job-4", bundle);
    }

    @Test
    @DisplayName("后台评分 sourceFileId 过期时写入 failed 且不调用评分 service")
    void evaluateLaterShouldMarkFailedWhenSourceFileMissing() {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService, fileStorageService);

        ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                .sourceFileId("missing-file")
                .build();
        when(fileStorageService.get("missing-file")).thenReturn(Optional.empty());

        asyncService.evaluateLater("job-5", request);

        verify(jobService).markRunning("job-5");
        verify(jobService).markFailed("job-5", "原始简历文件不存在或已过期: missing-file");
        verify(evaluationService, never()).evaluateWithoutJdStrict(org.mockito.ArgumentMatchers.any());
        verify(evaluationService, never()).evaluateWithJdStrict(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("后台评分 sourceFileId 指向图片时写入 failed")
    void evaluateLaterShouldMarkFailedWhenSourceFileIsImage() {
        ResumeEvaluationService evaluationService = mock(ResumeEvaluationService.class);
        ResumeEvaluationJobService jobService = mock(ResumeEvaluationJobService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        ResumeEvaluationAsyncService asyncService = new ResumeEvaluationAsyncService(evaluationService, jobService, fileStorageService);

        ResumeEvaluationRequest request = ResumeEvaluationRequest.builder()
                .sourceFileId("image-1")
                .build();
        ParsedFile parsedFile = ParsedFile.builder()
                .fileId("image-1")
                .fileName("resume.png")
                .fileKind("image")
                .success(true)
                .build();
        when(fileStorageService.get("image-1")).thenReturn(Optional.of(parsedFile));

        asyncService.evaluateLater("job-6", request);

        verify(jobService).markRunning("job-6");
        verify(jobService).markFailed("job-6", "原始简历文件必须是已解析文档: resume.png");
        verify(evaluationService, never()).evaluateWithoutJdStrict(org.mockito.ArgumentMatchers.any());
    }
}
