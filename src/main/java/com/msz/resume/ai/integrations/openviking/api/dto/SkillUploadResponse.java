package com.msz.resume.ai.integrations.openviking.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Skill 上传响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillUploadResponse {

    private String fileName;

    private Long fileSize;

    private String account;

    private String user;

    private String agent;

    private String status;

    private String message;

    private Map<String, Object> result;
}
