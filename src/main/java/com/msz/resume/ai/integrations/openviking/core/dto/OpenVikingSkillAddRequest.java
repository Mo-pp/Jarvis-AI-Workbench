package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenViking add_skill 请求体。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenVikingSkillAddRequest(
        Object data,
        @JsonProperty("temp_file_id") String tempFileId,
        @JsonProperty("wait") Boolean waitForProcessing,
        Double timeout
) {
}
