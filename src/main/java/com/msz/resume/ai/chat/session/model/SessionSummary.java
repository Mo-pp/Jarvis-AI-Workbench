package com.msz.resume.ai.chat.session.model;

import java.time.LocalDateTime;

/**
 * 会话摘要信息。
 *
 * 用于列表展示的精简会话数据，包含会话ID、标题、置顶状态和时间。
 */
public record SessionSummary(
        String sessionId,
        String title,
        boolean pinned,
        LocalDateTime createdAt,
        LocalDateTime lastActiveAt
) {
}
