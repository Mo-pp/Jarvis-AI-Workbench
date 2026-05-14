package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * OpenViking get session context 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingSessionContextResponse(
        String status,
        Map<String, Object> result,
        OpenVikingFindResponse.ErrorInfo error,
        Double time
) {
}
