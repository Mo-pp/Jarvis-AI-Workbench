-- V11: Persist oversized tool execution results referenced by L1 Tool Result Budget.

CREATE TABLE IF NOT EXISTS tool_result_blob (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工具结果大对象主键',
    session_id    VARCHAR(64)  NOT NULL COMMENT '关联会话ID',
    tool_name     VARCHAR(64)  NULL     COMMENT '工具名称',
    tool_call_id  VARCHAR(64)  NULL     COMMENT '工具调用ID',
    content       MEDIUMTEXT   NOT NULL COMMENT '完整工具执行结果内容',
    original_size INT          NOT NULL DEFAULT 0 COMMENT '原始大小（字符数）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_session_id_id (session_id, id),
    INDEX idx_tool_call_id (tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='超限工具执行结果存储表';
