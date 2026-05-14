package com.msz.resume.ai.integrations.openviking.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 资源列表项响应。
 */
@Data
@Builder
public class ResourceItemResponse {
    /**
     * 资源URI
     */
    private String uri;

    /**
     * 资源名称
     */
    private String name;

    /**
     * 资源大小（字节）
     */
    private Long size;

    /**
     * 是否为目录
     */
    private Boolean directory;

    /**
     * 资源类型
     */
    private String type;

    /**
     * 更新时间
     */
    private String updatedAt;

    /**
     * 摘要文本
     */
    private String abstractText;
}
