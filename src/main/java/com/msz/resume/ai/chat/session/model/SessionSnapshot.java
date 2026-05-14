/**
 * SessionSnapshot：会话快照
 * 用于从数据库恢复会话时，同时返回会话状态和消息历史
 * 是一个不可变的 record 类，适合数据传输
 */
package com.msz.resume.ai.chat.session.model;

import com.msz.resume.ai.chat.runtime.state.SessionState;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public record SessionSnapshot(
        SessionState state,         // 会话状态
        List<ChatMessage> messages, // LLM 消息历史
        List<HistoryMessage> historyMessages // UI 历史消息，包含不进 prompt 的 timeline actions
) {

    public SessionSnapshot(SessionState state, List<ChatMessage> messages) {
        this(state, messages, List.of());
    }

}
