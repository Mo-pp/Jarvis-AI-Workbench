package com.msz.resume.ai.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    /**
     * 文件ID，用于后续引用
     */
    private String fileId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件分类：document / image
     */
    private String fileKind;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private long fileSize;

    /**
     * 图片预览 data URL。仅用于短期前端预览，不进入消息持久化。
     */
    private String previewUrl;

    /**
     * 内容预览（前 200 字符）
     */
    private String contentPreview;

    /**
     * 是否解析成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 过期时间（秒）
     */
    private long expiresInSeconds;
}
