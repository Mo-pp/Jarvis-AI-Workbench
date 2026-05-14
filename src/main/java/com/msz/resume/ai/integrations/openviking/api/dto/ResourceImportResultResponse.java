package com.msz.resume.ai.integrations.openviking.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 资源导入结果响应。
 */
@Data
@Builder
public class ResourceImportResultResponse {
    /**
     * 来源类型：text / file / url
     */
    private String sourceType;

    /**
     * 来源名称（文件名或URL）
     */
    private String sourceName;

    /**
     * 目标URI
     */
    private String targetUri;

    /**
     * 根URI
     */
    private String rootUri;

    /**
     * 状态：success / conflict / failed
     */
    private String status;

    /**
     * 消息
     */
    private String message;
}
