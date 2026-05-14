package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenViking get task 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingTaskResponse(
        String status,
        Result result,
        OpenVikingFindResponse.ErrorInfo error,
        Double time
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("task_id") String taskId,
            @JsonProperty("task_type") String taskType,
            String status,
            @JsonProperty("resource_id") String resourceId,
            @JsonProperty("created_at") Double createdAt,
            @JsonProperty("updated_at") Double updatedAt,
            Map<String, Object> result,
            String error
    ) {
    }
}
