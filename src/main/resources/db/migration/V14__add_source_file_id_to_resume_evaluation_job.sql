-- V14: Track the uploaded source file used by async resume evaluation.

ALTER TABLE resume_evaluation_job
    ADD COLUMN source_file_id VARCHAR(64) NULL COMMENT '原始简历上传文件ID' AFTER status,
    ADD INDEX idx_resume_evaluation_source_file (source_file_id);
