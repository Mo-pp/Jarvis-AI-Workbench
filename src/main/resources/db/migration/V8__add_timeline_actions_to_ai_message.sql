-- V8: Persist user-visible chat timeline actions separately from LLM context messages.
ALTER TABLE ai_message
    ADD COLUMN timeline_actions_json JSON NULL COMMENT 'User-visible assistant timeline actions for history replay'
    AFTER tool_name;
