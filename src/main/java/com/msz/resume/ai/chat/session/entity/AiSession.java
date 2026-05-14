/**
 * AiSession 实体类：对应数据库 ai_session 表
 * 存储每个对话会话的元信息（状态、Token用量、时间等）
 */
package com.msz.resume.ai.chat.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_session")
public class AiSession {

    @TableId(type = IdType.INPUT)
    private String sessionId;

    private String ownerUsername;

    private String title;

    private String status;

    private Boolean pinned;

    private Integer totalInputTokens;

    private Integer totalOutputTokens;

    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;

    private LocalDateTime pinnedAt;
}
