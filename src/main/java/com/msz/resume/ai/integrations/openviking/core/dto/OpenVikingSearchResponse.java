package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * OpenViking session-aware search 响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingSearchResponse(
        String status,
        Result result,
        OpenVikingFindResponse.ErrorInfo error,
        Double time
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            List<OpenVikingFindResponse.MatchedContext> memories,
            List<OpenVikingFindResponse.MatchedContext> resources,
            List<OpenVikingFindResponse.MatchedContext> skills,
            Integer total
    ) {
    }
}
