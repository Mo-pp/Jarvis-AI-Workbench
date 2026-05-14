package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * OpenViking add_skill 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingSkillAddResponse(
        String status,
        Map<String, Object> result,
        ErrorInfo error,
        Double time
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(
            String code,
            String message,
            Map<String, Object> details
    ) {
    }
}
