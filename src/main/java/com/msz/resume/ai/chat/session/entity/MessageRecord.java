/**
 * MessageRecord 实体类：对应数据库 ai_message 表
 * 存储每个会话中的消息记录，不同消息类型通过 message_type 区分
 * 字段填充规则：
 *   UserMessage              → content
 *   AiMessage（纯文本）       → content
 *   AiMessage（含工具调用）    → content + toolCallsJson
 *   ToolExecutionResultMessage → toolResult + toolCallId + toolName
 *   SystemMessage             → content
 */
package com.msz.resume.ai.chat.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class MessageRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String messageType;

    private String content;

    private String toolCallsJson;

    private String toolResult;

    private String toolCallId;

    private String toolName;

    private String timelineActionsJson;

    private String attachmentsJson;

    private Integer tokenCount;

    private Boolean isCompressed;//是否压缩

    private LocalDateTime createdAt;
}
