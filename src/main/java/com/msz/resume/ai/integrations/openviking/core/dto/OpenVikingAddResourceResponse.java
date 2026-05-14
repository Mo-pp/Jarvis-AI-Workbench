package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenViking add_resource 响应。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingAddResourceResponse(
        @JsonProperty("status")
        String status,

        @JsonProperty("error")
        ErrorInfo error,

        @JsonProperty("result")
        Result result
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(
            @JsonProperty("message")
            String message,

            @JsonProperty("code")
            String code
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("root_uri")
            String rootUri,

            @JsonProperty("uri")
            String uri,

            @JsonProperty("status")
            String status,

            @JsonProperty("message")
            String message,

            @JsonProperty("task_id")
            String taskId
    ) {}
}
