-- V13: Persist async resume evaluation jobs so score polling survives refreshes.

CREATE TABLE IF NOT EXISTS resume_evaluation_job (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评分任务主键',
    job_id               VARCHAR(64)  NOT NULL COMMENT '外部查询用评分任务ID',
    session_id           VARCHAR(64)  NULL     COMMENT '关联会话ID',
    run_id               VARCHAR(64)  NULL     COMMENT '关联运行ID',
    status               VARCHAR(32)  NOT NULL COMMENT 'pending/running/success/failed',
    original_resume_text MEDIUMTEXT   NULL     COMMENT '原始简历文本',
    generated_resume_json MEDIUMTEXT  NULL     COMMENT '生成简历JSON',
    job_description      MEDIUMTEXT   NULL     COMMENT 'JD文本',
    target_position      VARCHAR(255) NULL     COMMENT '目标岗位',
    result_json          MEDIUMTEXT   NULL     COMMENT '评分结果JSON',
    error_message        TEXT         NULL     COMMENT '失败原因',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at         DATETIME     NULL     COMMENT '完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_evaluation_job_id (job_id),
    INDEX idx_resume_evaluation_session_updated (session_id, updated_at, id),
    INDEX idx_resume_evaluation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步简历评分任务表';
