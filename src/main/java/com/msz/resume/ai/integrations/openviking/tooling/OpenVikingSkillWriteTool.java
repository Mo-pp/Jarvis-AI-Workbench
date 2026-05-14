package com.msz.resume.ai.integrations.openviking.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenViking Skill 写入工具族（延迟工具）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenVikingSkillWriteTool {

    private static final int MAX_RESULT_CHARS = 4000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenVikingSkillService openVikingSkillService;

    @Tool("Create one uploaded OpenViking Skill from complete inline content. This is a deferred tool: use only when the user explicitly asks to create/add/save a Skill and has roughly agreed. The data argument must contain the full Skill content now, either a complete SKILL.md string or a complete structured Skill object serialized as JSON. Do not pass references like 'the previous content', local file paths, server paths, multipart files, zip files, or URLs. This tool does not upload files and does not overwrite existing Skills by design; if OpenViking reports a conflict, return that conflict to the user.")
    public String openviking_skill_add(
            @P("Complete Skill content to add. Pass the full SKILL.md text, or a complete structured Skill object serialized as JSON. Must not be a local path, file path, zip path, URL, or reference to earlier chat content.") String data
    ) {
        log.info("[OpenVikingSkillWriteTool] openviking_skill_add dataLength={}", data != null ? data.length() : null);
        if (!hasText(data)) {
            return "OpenViking Skill add failed: data is empty.";
        }
        String trimmedData = data.trim();
        if (looksLikeReference(trimmedData)) {
            return "OpenViking Skill add failed: data must contain the full Skill content, not a reference to previous content.";
        }
        if (looksLikeUnsupportedReference(trimmedData)) {
            return "OpenViking Skill add failed: data must contain inline Skill content, not a path, URL, zip, or upload reference.";
        }
        try {
            OpenVikingSkillAddResponse response = openVikingSkillService.addSkill(parseSkillData(trimmedData));
            return formatAddResponse(response);
        } catch (IllegalArgumentException | OpenVikingClientException e) {
            log.warn("[OpenVikingSkillWriteTool] Skill 添加失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSkillWriteTool] Skill 添加异常", e);
            return "OpenViking Skill add failed: unexpected error.";
        }
    }


    private Object parseSkillData(String data) {
        if (!(data.startsWith("{") || data.startsWith("["))) {
            return data;
        }
        try {
            return OBJECT_MAPPER.readValue(data, Object.class);
        } catch (JsonProcessingException e) {
            return data;
        }
    }

    private String formatAddResponse(OpenVikingSkillAddResponse response) {
        if (response == null) {
            return "OpenViking Skill add failed: empty response.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("OpenViking Skill add result\n");
        sb.append("status=").append(response.status()).append("\n");
        if (response.error() != null) {
            sb.append("error=").append(response.error().message()).append("\n");
        }
        sb.append("result=");
        if (response.result() == null || response.result().isEmpty()) {
            sb.append("empty");
        } else {
            sb.append("\n").append(truncate(serialize(response.result()), MAX_RESULT_CHARS));
        }
        return sb.toString();
    }

    private String serialize(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private boolean looksLikeReference(String data) {
        String lower = data.toLowerCase();
        return lower.equals("previous")
                || lower.equals("above")
                || lower.equals("same as above")
                || lower.equals("the previous content")
                || lower.equals("刚刚那份")
                || lower.equals("上面的内容")
                || lower.equals("同上")
                || lower.equals("如上");
    }

    private boolean looksLikeUnsupportedReference(String data) {
        String lower = data.toLowerCase();
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("file:")
                || lower.endsWith(".zip")
                || lower.contains(".zip#")
                || lower.startsWith("upload:")
                || lower.startsWith("multipart:");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
