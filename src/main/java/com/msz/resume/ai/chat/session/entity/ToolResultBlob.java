package com.msz.resume.ai.chat.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ToolResultBlob 实体类：存储超限的工具执行结果
 *
 * <p>当工具返回结果超过阈值时，完整内容存储到此表，
 * 消息历史中只保留预览和引用ID。
 */
@Data
@TableName("tool_result_blob")
public class ToolResultBlob {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 工具名称 */
    private String toolName;

    /** 工具调用ID（对应 ToolExecutionRequest.id） */
    private String toolCallId;

    /** 完整结果内容 */
    private String content;

    /** 原始大小（字符数） */
    private Integer originalSize;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /**
     * 创建新记录
     */
    public static ToolResultBlob of(String sessionId, String toolName, String toolCallId, String content, int originalSize) {
        ToolResultBlob blob = new ToolResultBlob();
        blob.setSessionId(sessionId);
        blob.setToolName(toolName);
        blob.setToolCallId(toolCallId);
        blob.setContent(content);
        blob.setOriginalSize(originalSize);
        blob.setCreatedAt(LocalDateTime.now());
        return blob;
    }
}
