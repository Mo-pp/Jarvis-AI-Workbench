package com.msz.resume.ai.chat.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 对话请求DTO
 * 接收前端发送的对话请求参数
 */
@Data
public class ChatRequest {
    /**
     * 会话ID（可选，不传则自动生成）
     * 用于支持会话恢复，同一sessionId可以继续之前的对话
     */
    private String sessionId;

    /**
     * 用户消息内容
     * 用户的提问或指令
     */
    private String userMessage;

    /**
     * 用户ID（可选，不传则使用匿名ID）
     * 后续对接用户系统时，从JWT token中解析
     */
    private String userId;

    /**
     * 用户名（可选）
     * 显示在系统提示词的 user_context section 中
     */
    private String username;

    /**
     * 语言偏好（可选，默认 zh-CN）
     */
    private String language;

    /**
     * 输出风格偏好（可选，默认 concise）
     */
    private String outputStyle;

    /**
     * 本轮是否开启思考模式。
     * true 会请求更高 reasoning effort，false 会显式关闭/降低 reasoning effort。
     */
    private Boolean thinkingMode;

    /**
     * 本轮推理强度覆盖值（可选）。
     * 前端默认只传 thinkingMode；该字段保留给调试或未来细粒度选择。
     */
    private String reasoningEffort;

    /**
     * 关联的文件ID（可选）
     * 用户上传文件后，前端获得 fileId，在对话时传入
     * 后端会自动将文件内容注入到消息上下文中
     */
    private String fileId;

    /**
     * 图片附件 ID 列表。图片通过 /api/files/upload 上传后短期存 Redis，
     * 对话时后端会将其组装为多模态 ImageContent。
     */
    private List<String> imageFileIds;

    /**
     * 通用附件 ID 列表，当前主要用于图片；保留扩展空间。
     */
    private List<String> attachmentIds;
}
