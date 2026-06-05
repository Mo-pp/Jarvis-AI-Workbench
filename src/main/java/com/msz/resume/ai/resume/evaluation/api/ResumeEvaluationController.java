package com.msz.resume.ai.resume.evaluation.api;

import com.msz.resume.ai.resume.evaluation.dto.BatchResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.CandidateResumeEvaluationCard;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.service.ResumeEvaluationService;
import com.msz.resume.ai.shared.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/resume/evaluations")
public class ResumeEvaluationController {

    private final ResumeEvaluationService evaluationService;

    public ResumeEvaluationController(ResumeEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
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
}
