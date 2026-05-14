package com.msz.resume.ai.integrations.openviking.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * OpenViking 配置属性。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jarvis.open-viking")
public class OpenVikingProperties {

    /** OpenViking 服务基础地址 */
    private String baseUrl = "";

    /** OpenViking API Key */
    private String apiKey;

    /** HTTP 请求超时时间 */
    private Duration timeout = Duration.ofSeconds(10);

    /** 默认返回条数 */
    private int defaultLimit = 5;

    /** 最大返回条数 */
    private int maxLimit = 8;

    /** 工具结果最大字符数 */
    private int maxResultChars = 8000;

    /** 可选租户账号 */
    private String account;

    /** 可选租户用户 */
    private String user;

    /** 可选租户 agent */
    private String agent = "jarvis";
}
