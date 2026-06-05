package com.msz.resume.ai.chat.session.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.compression.model.LlmContextCheckpoint;
import com.msz.resume.ai.chat.session.converter.ChatMessageConverter;
import com.msz.resume.ai.chat.session.entity.AiContextCheckpoint;
import com.msz.resume.ai.chat.session.entity.MessageRecord;
import com.msz.resume.ai.chat.session.mapper.AiContextCheckpointMapper;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class LlmContextCheckpointService {

    private final AiContextCheckpointMapper mapper;
    private final ChatMessageConverter messageConverter;
    private final ObjectMapper objectMapper;

    public LlmContextCheckpointService(AiContextCheckpointMapper mapper,
                                       ChatMessageConverter messageConverter,
                                       ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(String sessionId, LlmContextCheckpoint checkpoint) {
        if (sessionId == null || sessionId.isBlank() || checkpoint == null || !checkpoint.hasSummary()) {
            return;
        }

        try {
            List<MessageRecord> summaryRecords = messageConverter.toMessageRecordList(sessionId, checkpoint.summaryMessages());

            AiContextCheckpoint entity = new AiContextCheckpoint();
            entity.setSessionId(sessionId);
            entity.setTailStartIndex(checkpoint.tailStartIndex());
            entity.setSourceMessageCount(checkpoint.sourceMessageCount());
            entity.setSummaryMessagesJson(objectMapper.writeValueAsString(summaryRecords));
            entity.setOriginalTokens(checkpoint.originalTokens());
            entity.setCompactedTokens(checkpoint.compactedTokens());
            LocalDateTime now = LocalDateTime.now();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            mapper.upsert(entity);
            log.info("[LlmContextCheckpointService] saved checkpoint: sessionId={}, tailStart={}, sourceMessages={}, summaryMessages={}",
                    sessionId, checkpoint.tailStartIndex(), checkpoint.sourceMessageCount(), checkpoint.summaryMessages().size());
        } catch (JsonProcessingException e) {
            log.warn("[LlmContextCheckpointService] checkpoint serialize failed: sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
    }

    public LlmContextCheckpoint load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        AiContextCheckpoint entity = mapper.selectBySessionId(sessionId);
        if (entity == null || entity.getSummaryMessagesJson() == null || entity.getSummaryMessagesJson().isBlank()) {
            return null;
        }

        try {
            List<MessageRecord> records = objectMapper.readValue(
                    entity.getSummaryMessagesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MessageRecord.class)
            );
            List<ChatMessage> summaryMessages = messageConverter.toChatMessageList(records);
            return new LlmContextCheckpoint(
                    intValue(entity.getTailStartIndex()),
                    intValue(entity.getSourceMessageCount()),
                    summaryMessages,
                    intValue(entity.getOriginalTokens()),
                    intValue(entity.getCompactedTokens())
            );
        } catch (Exception e) {
            log.warn("[LlmContextCheckpointService] checkpoint load failed: sessionId={}, error={}",
                    sessionId, e.getMessage());
            return null;
        }
    }

    private int intValue(Integer value) {
        return value != null ? value : 0;
    }
}
