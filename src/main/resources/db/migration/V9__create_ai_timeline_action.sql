-- V9: Store replayable UI timeline actions outside LLM prompt message history.
CREATE TABLE IF NOT EXISTS ai_timeline_action (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Timeline action primary key',
    session_id           VARCHAR(64)  NOT NULL COMMENT 'Associated session id',
    action_id            VARCHAR(191) NOT NULL COMMENT 'Stable frontend action id',
    anchor_message_index INT          NOT NULL DEFAULT -1 COMMENT 'Zero-based ai_message index to attach to, or -1 for UI-only pending actions',
    event_type           VARCHAR(64)  NOT NULL COMMENT 'Last timeline event type',
    kind                 VARCHAR(64)  NOT NULL COMMENT 'Frontend action kind',
    first_sequence       BIGINT       NULL COMMENT 'First SSE sequence for ordering',
    sequence             BIGINT       NULL COMMENT 'Latest SSE sequence for ordering',
    status               VARCHAR(32)  NULL COMMENT 'Latest action status',
    payload_json         JSON         NOT NULL COMMENT 'Sanitized frontend timeline payload',
    prompt_visible       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'Whether this action may be used in LLM prompt history',
    persistable          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT 'Whether this action should be persisted',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_action (session_id, action_id),
    INDEX idx_session_anchor_order (session_id, anchor_message_index, first_sequence, sequence),
    INDEX idx_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI-only chat timeline action table';
