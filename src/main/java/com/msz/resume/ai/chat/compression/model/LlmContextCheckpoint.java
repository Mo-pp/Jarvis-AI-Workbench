package com.msz.resume.ai.chat.compression.model;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Model-facing compact checkpoint.
 *
 * <p>The regular chat history stays complete for UI/history replay. This record
 * stores the shorter LLM projection prefix plus the original-message tail index
 * to append after that prefix.
 */
public record LlmContextCheckpoint(
        int tailStartIndex,
        int sourceMessageCount,
        List<ChatMessage> summaryMessages,
        int originalTokens,
        int compactedTokens
) {

    public static LlmContextCheckpoint empty() {
        return new LlmContextCheckpoint(-1, 0, List.of(), 0, 0);
    }

    public boolean hasSummary() {
        return summaryMessages != null && !summaryMessages.isEmpty() && tailStartIndex >= 0;
    }
}
