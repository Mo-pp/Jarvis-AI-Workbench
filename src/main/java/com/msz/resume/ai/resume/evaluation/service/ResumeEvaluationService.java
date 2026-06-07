package com.msz.resume.ai.resume.evaluation.service;

import com.msz.resume.ai.resume.evaluation.dto.BatchResumeEvaluationRequest;
import com.msz.resume.ai.resume.evaluation.dto.CandidateResumeEvaluationCard;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationBundle;
import com.msz.resume.ai.resume.evaluation.dto.ResumeEvaluationRequest;

import java.util.List;

public interface ResumeEvaluationService {

    ResumeEvaluationBundle evaluateWithoutJd(ResumeEvaluationRequest request);

    ResumeEvaluationBundle evaluateWithJd(ResumeEvaluationRequest request);

    default ResumeEvaluationBundle evaluateWithoutJdStrict(ResumeEvaluationRequest request) {
        return evaluateWithoutJd(request);
    }

    default ResumeEvaluationBundle evaluateWithJdStrict(ResumeEvaluationRequest request) {
        return evaluateWithJd(request);
    }

    List<CandidateResumeEvaluationCard> evaluateBatchWithJd(BatchResumeEvaluationRequest request);
}
