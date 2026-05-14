package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenViking append session message 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingAppendSessionMessageResponse(
        String status,
        Result result,
        OpenVikingFindResponse.ErrorInfo error,
        Double time
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("message_count") Integer messageCount
    ) {
    }
}
