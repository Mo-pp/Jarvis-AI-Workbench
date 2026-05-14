package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OpenViking find 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingFindResponse(
        String status,
        Result result,
        ErrorInfo error,
        Double time
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            List<MatchedContext> memories,
            List<MatchedContext> resources,
            List<MatchedContext> skills,
            Integer total
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchedContext(
            String uri,
            @JsonProperty("context_type") String contextType,
            @JsonProperty("is_leaf") Boolean isLeaf,
            @JsonProperty("abstract") String abstractText,
            String category,
            Double score,
            @JsonProperty("match_reason") String matchReason
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
