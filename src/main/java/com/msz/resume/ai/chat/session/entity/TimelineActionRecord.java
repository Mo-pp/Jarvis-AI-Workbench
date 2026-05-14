package com.msz.resume.ai.chat.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI-only chat timeline action record.
 *
 * These records are replay metadata for the frontend and are never converted
 * into LangChain4j ChatMessage prompt history.
 */
@Data
@TableName("ai_timeline_action")
public class TimelineActionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String actionId;

    private Integer anchorMessageIndex;

    private String eventType;

    private String kind;

    private Long firstSequence;

    private Long sequence;

    private String status;

    private String payloadJson;

    private Boolean promptVisible;

    private Boolean persistable;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
