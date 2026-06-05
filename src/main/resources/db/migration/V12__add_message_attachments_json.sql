ALTER TABLE ai_message
    ADD COLUMN attachments_json JSON NULL COMMENT 'User-visible attachment metadata for multimodal messages' AFTER timeline_actions_json;
