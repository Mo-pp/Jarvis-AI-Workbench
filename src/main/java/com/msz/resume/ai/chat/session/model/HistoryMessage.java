package com.msz.resume.ai.chat.session.model;

import com.msz.resume.ai.chat.session.entity.MessageRecord;
import dev.langchain4j.data.message.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Database-backed history item used for UI replay metadata.
 */
public record HistoryMessage(
        MessageRecord record,
        ChatMessage message,
        List<Map<String, Object>> timelineActions,
        boolean uiOnly,
        LocalDateTime createdAt
) {

    public HistoryMessage(MessageRecord record,
                          ChatMessage message,
                          List<Map<String, Object>> timelineActions) {
        this(
                record,
                message,
                timelineActions,
                false,
                record != null ? record.getCreatedAt() : null
        );
    }

    public static HistoryMessage uiOnly(List<Map<String, Object>> timelineActions, LocalDateTime createdAt) {
        return new HistoryMessage(null, null, timelineActions, true, createdAt);
    }
}
