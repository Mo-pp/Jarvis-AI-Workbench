package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenViking create session 请求体。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenVikingCreateSessionRequest(
        @JsonProperty("session_id") String sessionId
) {
}
