package com.msz.resume.ai.resume.evaluation.api;

import com.msz.resume.ai.resume.evaluation.dto.BatchResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.CandidateResumeEvaluationCard;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationJobStatusResponse;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationJobService;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationService;
import com.msz.resume.ai.shared.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping({"/api/resume/evaluations", "/api/resume/evaluation"})
public class ResumeEvaluationController {

    private final ResumeEvaluationService evaluationService;
    private final ResumeEvaluationJobService jobService;

    public ResumeEvaluationController(ResumeEvaluationService evaluationService,
                                      ResumeEvaluationJobService jobService) {
        this.evaluationService = evaluationService;
        this.jobService = jobService;
    }

    @GetMapping("/status")
    public Result<ResumeEvaluationJobStatusResponse> getStatus(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String sessionId
    ) {
        if (!hasText(jobId) && !hasText(sessionId)) {
            return Result.error("jobId 或 sessionId 不能为空");
        }
        Optional<ResumeEvaluationJobStatusResponse> response = jobService.findStatus(jobId, sessionId);
        return response.map(Result::success).orElseGet(() -> Result.error("评分任务不存在"));
    }

    @PostMapping("/no-jd")
    public Result<ResumeEvaluationBundle> evaluateWithoutJd(@RequestBody ResumeEvaluationRequest request) {
        try {
            return Result.success(evaluationService.evaluateWithoutJd(request));
        } catch (Exception e) {
            log.warn("[ResumeEvaluationController] 无 JD 评分失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/with-jd")
    public Result<ResumeEvaluationBundle> evaluateWithJd(@RequestBody ResumeEvaluationRequest request) {
        try {
            return Result.success(evaluationService.evaluateWithJd(request));
        } catch (Exception e) {
            log.warn("[ResumeEvaluationController] 带 JD 评分失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/batch-with-jd")
    public Result<List<CandidateResumeEvaluationCard>> evaluateBatchWithJd(@RequestBody BatchResumeEvaluationRequest request) {
        try {
            return Result.success(evaluationService.evaluateBatchWithJd(request));
        } catch (Exception e) {
            log.warn("[ResumeEvaluationController] 批量带 JD 评分失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
