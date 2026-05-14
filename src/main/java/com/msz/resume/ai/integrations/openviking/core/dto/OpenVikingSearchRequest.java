package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenViking session-aware search 请求体。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenVikingSearchRequest(
        String query,
        @JsonProperty("target_uri") Object targetUri,
        @JsonProperty("session_id") String sessionId,
        Integer limit,
        @JsonProperty("node_limit") Integer nodeLimit,
        @JsonProperty("score_threshold") Double scoreThreshold,
        Map<String, Object> filter,
        @JsonProperty("include_provenance") Boolean includeProvenance,
        String since,
        String until,
        @JsonProperty("time_field") String timeField
) {
}
