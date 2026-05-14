ALTER TABLE ai_session
    ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether the session is pinned' AFTER status,
    ADD COLUMN pinned_at DATETIME NULL COMMENT 'Last time the session was pinned' AFTER last_active_at;

CREATE INDEX idx_session_pin_order ON ai_session (status, pinned, pinned_at, last_active_at);
