package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenViking create session 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingCreateSessionResponse(
        String status,
        Result result,
        OpenVikingFindResponse.ErrorInfo error,
        Double time
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("session_id") String sessionId,
            Map<String, Object> user
    ) {
    }
}
