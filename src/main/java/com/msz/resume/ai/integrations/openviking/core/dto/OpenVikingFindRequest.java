package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenViking find 请求体。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenVikingFindRequest(
        String query,
        @JsonProperty("target_uri") String targetUri,
        Integer limit,
        @JsonProperty("node_limit") Integer nodeLimit,
        @JsonProperty("score_threshold") Double scoreThreshold,
        @JsonProperty("include_provenance") Boolean includeProvenance
) {
}
