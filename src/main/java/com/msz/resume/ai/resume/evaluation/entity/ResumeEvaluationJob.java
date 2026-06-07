package com.msz.resume.ai.resume.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_evaluation_job")
public class ResumeEvaluationJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobId;

    private String sessionId;

    private String runId;

    private String status;

    private String originalResumeText;

    private String generatedResumeJson;

    private String jobDescription;

    private String targetPosition;

    private String resultJson;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
}
