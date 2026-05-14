package com.msz.resume.ai.chat.runtime.trace.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.session.entity.TimelineActionRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

@Component
public class TimelineActionRecordProjector {

    private final ObjectMapper objectMapper;

    public TimelineActionRecordProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<TimelineActionRecord> project(TraceStreamEvent event) {
        if (event == null || event.sessionId() == null || event.sessionId().isBlank()
                || event.actionId() == null || event.actionId().isBlank()
                || event.payload() == null || event.payload().isEmpty()) {
            return Optional.empty();
        }

        try {
            TimelineActionRecord record = new TimelineActionRecord();
            record.setSessionId(event.sessionId());
            record.setActionId(event.actionId());
            record.setAnchorMessageIndex(event.anchorMessageIndex());
            record.setEventType(stringValue(event.payload().get("eventType"), event.eventType()));
            record.setKind(stringValue(event.payload().get("kind"), ""));
            record.setFirstSequence(event.firstSequence());
            record.setSequence(event.sequence());
            record.setStatus(stringValue(event.payload().get("status"), ""));
            record.setPayloadJson(objectMapper.writeValueAsString(event.payload()));
            record.setPromptVisible(booleanValue(event.payload().getOrDefault("promptVisible", false)));
            record.setPersistable(booleanValue(event.payload().getOrDefault("persistable", true)));
            LocalDateTime createdAt = LocalDateTime.ofInstant(event.createdAt(), ZoneId.systemDefault());
            record.setCreatedAt(createdAt);
            record.setUpdatedAt(LocalDateTime.now());
            return Optional.of(record);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringValue(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }
}
