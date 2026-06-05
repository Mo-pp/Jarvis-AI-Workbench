package com.msz.resume.ai.chat.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LLM-only compact checkpoint for a chat session.
 */
@Data
@TableName("ai_context_checkpoint")
public class AiContextCheckpoint {

    @TableId(type = IdType.INPUT)
    private String sessionId;

    private Integer tailStartIndex;

    private Integer sourceMessageCount;

    private String summaryMessagesJson;

    private Integer originalTokens;

    private Integer compactedTokens;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
