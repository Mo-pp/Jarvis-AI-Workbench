package com.msz.resume.ai.chat.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话摘要响应 DTO。
 *
 * 用于会话列表展示的精简信息。
 */
@Data
@Builder
public class SessionSummaryResponse {
    /** 会话ID */
    private String sessionId;
    /** 会话标题 */
    private String title;
    /** 是否置顶 */
    private boolean pinned;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;
}
