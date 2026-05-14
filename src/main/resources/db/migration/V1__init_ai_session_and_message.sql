-- V1: 创建 ai_session（会话表）和 ai_message（消息表）

-- 会话表：存储每个对话会话的元信息
CREATE TABLE IF NOT EXISTS ai_session (
                                          session_id         VARCHAR(64)   NOT NULL COMMENT '会话唯一标识（UUID）',
                                          title              VARCHAR(200)  NULL     COMMENT '会话标题（预留，后续实现）',
                                          status             VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '会话状态：active / closed / expired',
                                          total_input_tokens INT           NOT NULL DEFAULT 0 COMMENT '累计输入 Token 数',
                                          total_output_tokens INT          NOT NULL DEFAULT 0 COMMENT '累计输出 Token 数',
                                          created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          last_active_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
                                          PRIMARY KEY (session_id),
                                          INDEX idx_status (status),
                                          INDEX idx_last_active_at (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI会话表';

-- 消息表：存储每个会话中的消息记录
CREATE TABLE IF NOT EXISTS ai_message (
                                          id                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '消息自增主键',
                                          session_id        VARCHAR(64)    NOT NULL COMMENT '关联会话ID',
                                          message_type      VARCHAR(30)    NOT NULL COMMENT '消息类型：UserMessage / AiMessage / ToolExecutionResultMessage / SystemMessage',
                                          content           TEXT           NULL     COMMENT '消息文本内容',
                                          tool_calls_json   JSON           NULL     COMMENT 'AiMessage的工具调用请求列表（仅AiMessage有值）',
                                          tool_result       MEDIUMTEXT     NULL     COMMENT '工具执行结果（仅ToolExecutionResultMessage有值，大内容分离存储）',
                                          tool_call_id      VARCHAR(64)    NULL     COMMENT '工具调用ID（仅ToolExecutionResultMessage有值）',
                                          tool_name         VARCHAR(64)    NULL     COMMENT '工具名称（仅ToolExecutionResultMessage有值）',
                                          token_count       INT            NULL     DEFAULT 0 COMMENT '该条消息的Token数（预留，用于上下文工程）',
                                          is_compressed     TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否已被压缩（预留，用于上下文工程）',
                                          created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间',
                                          PRIMARY KEY (id),
                                          INDEX idx_session_id_id (session_id, id),
                                          INDEX idx_tool_call_id (tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI消息表';
