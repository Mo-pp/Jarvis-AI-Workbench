package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OpenViking append session message 请求体。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenVikingAppendSessionMessageRequest(
        String role,
        @JsonProperty("role_id") String roleId,
        String content,
        List<Map<String, Object>> parts,
        @JsonProperty("created_at") String createdAt
) {
}
