package com.msz.resume.ai.integrations.mcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jarvis.mcp")
public class McpProperties {

    private boolean enabled = false;
    private boolean failFast = false;
    private List<Server> servers = new ArrayList<>();

    @Getter
    @Setter
    public static class Server {
        private boolean enabled = true;
        private String key;
        private Transport transport = Transport.SSE_HTTP;
        private String url;
        private List<String> command = new ArrayList<>();
        private Map<String, String> environment = new LinkedHashMap<>();
        private Map<String, String> headers = new LinkedHashMap<>();
        private String toolPrefix;
        private Exposure exposure = Exposure.DEFERRED;
        private List<String> allowTools = new ArrayList<>();
        private List<String> denyTools = new ArrayList<>();
        private Duration timeout = Duration.ofSeconds(60);
        private Duration initializationTimeout = Duration.ofSeconds(30);
        private Duration toolExecutionTimeout = Duration.ofSeconds(60);
        private boolean logRequests = false;
        private boolean logResponses = false;
        private boolean cacheToolList = true;
        private Resources resources = new Resources();
    }

    @Getter
    @Setter
    public static class Resources {
        private boolean enabled = true;
        private Exposure exposure = Exposure.DEFERRED;
        private int maxTextChars = 20000;
        private int maxBlobChars = 2000;
    }

    public enum Transport {
        SSE_HTTP,
        STREAMABLE_HTTP,
        STDIO
    }

    public enum Exposure {
        CORE,
        DEFERRED
    }
}
