package com.msz.resume.ai.integrations.openviking.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 资源库配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jarvis.resource")
public class ResourceProperties {

    /**
     * 纯文本内容最大字节数（默认 5MB）。
     */
    private long maxTextContentBytes = 5 * 1024 * 1024;

    /**
     * 上传文件最大字节数（默认 250MB）。
     */
    private long maxFileSizeBytes = 250 * 1024 * 1024;

    /**
     * 预览内容最大字符数（默认 100KB）。
     */
    private int maxPreviewChars = 100_000;

    /**
     * 文件名最大长度（默认 200）。
     */
    private int maxFilenameLength = 200;
}
