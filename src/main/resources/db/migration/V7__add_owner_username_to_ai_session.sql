ALTER TABLE ai_session
    ADD COLUMN owner_username VARCHAR(100) NULL COMMENT '会话所属用户名' AFTER session_id;

UPDATE ai_session
SET owner_username = 'u-demo-001'
WHERE owner_username IS NULL;

ALTER TABLE ai_session
    MODIFY COLUMN owner_username VARCHAR(100) NOT NULL COMMENT '会话所属用户名';

CREATE INDEX idx_session_owner_status_active
    ON ai_session (owner_username, status, pinned, pinned_at, last_active_at);
