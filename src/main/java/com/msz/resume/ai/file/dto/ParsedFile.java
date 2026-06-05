package com.msz.resume.ai.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 已解析的文件内容
 *
 * <p>存储在 Redis 中，15 分钟后自动过期。
 * 包含解析后的纯文本内容，供 LLM 直接使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件唯一标识
     */
    private String fileId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型（扩展名）
     */
    private String fileType;

    /**
     * 文件分类：document / image
     */
    private String fileKind;

    /**
     * MIME 类型，图片附件会用于构造多模态输入。
     */
    private String mimeType;

    /**
     * 文件大小（字节）
     */
    private long fileSize;

    /**
     * 解析后的纯文本内容
     */
    private String content;

    /**
     * 图片文件的 base64 内容。只存在 Redis 短期缓存中，不写入消息历史。
     */
    private String base64Data;

    /**
     * 解析时间
     */
    private Instant parsedAt;

    /**
     * 是否解析成功
     */
    private boolean success;

    /**
     * 错误信息（解析失败时）
     */
    private String errorMessage;
}
