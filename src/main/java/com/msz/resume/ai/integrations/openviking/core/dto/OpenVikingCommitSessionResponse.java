package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenViking commit session 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingCommitSessionResponse(
        String status,
        Result result,
        OpenVikingFindResponse.ErrorInfo error,
        Double time
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String status,
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("task_id") String taskId,
            Boolean archived,
            Map<String, Object> details
    ) {
    }
}
