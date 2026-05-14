package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenViking temp_upload 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingTempUploadResponse(
        String status,
        Result result,
        ErrorInfo error,
        Double time
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("temp_file_id") String tempFileId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(
            String code,
            String message,
            Map<String, Object> details
    ) {
    }
}
