package com.msz.resume.ai.integrations.openviking.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 列表项响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillListItemResponse {

    private String id;

    private String name;

    private String path;

    @JsonProperty("abstract")
    private String abstractText;

    private String updatedAt;
}
