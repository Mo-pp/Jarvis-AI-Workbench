package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.LlmContextCheckpoint;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the model-facing context from a compact checkpoint.
 */
@Slf4j
@Component
public class LlmContextProjector {

    public List<ChatMessage> project(List<ChatMessage> fullHistory, LlmContextCheckpoint checkpoint, String sessionId) {
        return projectWithMetadata(fullHistory, checkpoint, sessionId).messages();
    }

    public Projection projectWithMetadata(List<ChatMessage> fullHistory, LlmContextCheckpoint checkpoint, String sessionId) {
        if (fullHistory == null || fullHistory.isEmpty()) {
            return new Projection(fullHistory != null ? fullHistory : List.of(), false, 0, 0);
        }
        if (checkpoint == null || !checkpoint.hasSummary()) {
            return new Projection(fullHistory, false, 0, 0);
        }

        int tailStartIndex = checkpoint.tailStartIndex();
        if (tailStartIndex > fullHistory.size()) {
            log.warn("[LlmContextProjector] checkpoint ignored: sessionId={}, tailStartIndex={}, messages={}",
                    sessionId, tailStartIndex, fullHistory.size());
            return new Projection(fullHistory, false, 0, 0);
        }

        List<ChatMessage> projected = new ArrayList<>();
        projected.addAll(checkpoint.summaryMessages());
        projected.addAll(fullHistory.subList(tailStartIndex, fullHistory.size()));

        log.debug("[LlmContextProjector] projected context: sessionId={}, full={}, summary={}, tailStart={}, projected={}",
                sessionId, fullHistory.size(), checkpoint.summaryMessages().size(), tailStartIndex, projected.size());
        return new Projection(projected, true, tailStartIndex, checkpoint.summaryMessages().size());
    }

    public record Projection(
            List<ChatMessage> messages,
            boolean checkpointApplied,
            int fullHistoryTailStart,
            int summaryPrefixSize
    ) {
    }
}
