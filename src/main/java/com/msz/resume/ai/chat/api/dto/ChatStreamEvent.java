package com.msz.resume.ai.chat.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * SSE 流式对话事件。
 *
 * <p>前端协议统一使用该 envelope，避免解析中文裸字符串。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent {

    /** 事件类型，如 session_started/message_done/done/error */
    private String type;

    /** 会话 ID */
    private String sessionId;

    /** 单个 SSE 连接内递增序号 */
    private long sequence;

    /** 事件产生时间 */
    private Instant timestamp;

    /** 事件载荷 */
    private Map<String, Object> payload;
}
