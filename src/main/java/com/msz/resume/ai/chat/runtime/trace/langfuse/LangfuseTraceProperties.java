package com.msz.resume.ai.chat.runtime.trace.langfuse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jarvis.trace.langfuse")
public class LangfuseTraceProperties {

    private boolean enabled = false;
    private String baseUrl = "https://cloud.langfuse.com";
    private String publicKey = "";
    private String secretKey = "";
    private String environment = "dev";
    private String serviceName = "jarvis";
    private String release = "local";
    private long exportTimeoutMs = 5000;

    public boolean configured() {
        return enabled
                && hasText(baseUrl)
                && hasText(publicKey)
                && hasText(secretKey);
    }

    public String tracesEndpoint() {
        String normalized = baseUrl != null ? baseUrl.trim().replaceAll("/+$", "") : "";
        return normalized + "/api/public/otel/v1/traces";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
