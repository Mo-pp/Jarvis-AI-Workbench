-- V10: Persist model-facing compact checkpoints separately from user-visible chat history.

CREATE TABLE IF NOT EXISTS ai_context_checkpoint (
    session_id            VARCHAR(64) NOT NULL COMMENT '会话唯一标识',
    tail_start_index      INT         NOT NULL COMMENT '原始消息列表中未被摘要尾部的起始下标',
    source_message_count  INT         NOT NULL COMMENT '生成 checkpoint 时的原始消息总数',
    summary_messages_json JSON        NOT NULL COMMENT '模型上下文摘要前缀消息列表',
    original_tokens       INT         NOT NULL DEFAULT 0 COMMENT '压缩前 token 数',
    compacted_tokens      INT         NOT NULL DEFAULT 0 COMMENT '压缩后 token 数',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM上下文压缩checkpoint表';
