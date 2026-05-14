package com.msz.resume.ai.integrations.openviking.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenViking Skill 读取工具族（核心工具）。
 */
@Slf4j
@CoreTool
@Component
@RequiredArgsConstructor
public class OpenVikingSkillTool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int MAX_RESULT_CHARS = 8000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenVikingSkillService openVikingSkillService;

    @Tool("Search uploaded OpenViking Skills under viking://agent/skills/. Use this only when the user explicitly asks to use a Skill or refers to an uploaded Skill. This returns candidate Skill names/URIs; after finding a candidate, read its SKILL.md with openviking_skill_read before following its instructions.")
    public String openviking_skill_search(
            @P("Search query for the Skill catalog, usually the Skill name, topic, or the user's explicit Skill request.") String query,
            @P(value = "Maximum number of Skill matches. Recommended 3 to 5.", required = false) Integer limit
    ) {
        log.info("[OpenVikingSkillTool] openviking_skill_search query={}, limit={}", query, limit);
        if (!hasText(query)) {
            return "OpenViking Skill search failed: query is empty.";
        }
        String limitError = validatePositiveLimit("search", "limit", limit);
        if (limitError != null) {
            return limitError;
        }
        try {
            OpenVikingFindResponse response = openVikingSkillService.searchSkills(query, sanitizeLimit(limit));
            return formatFindResponse(query.trim(), response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSkillTool] Skill 检索失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSkillTool] Skill 检索异常", e);
            return "OpenViking Skill search failed: unexpected error.";
        }
    }

    @Tool("Read the full SKILL.md for one uploaded OpenViking Skill by exact Skill name. Use this after openviking_skill_search identifies the Skill. Follow the instructions in SKILL.md, and if it references supporting files, inspect the file tree and read those files with openviking_skill_read_file.")
    public String openviking_skill_read(
            @P("Exact Skill directory name under viking://agent/skills/, not a URI and not a file path.") String name
    ) {
        log.info("[OpenVikingSkillTool] openviking_skill_read name={}", name);
        if (!hasText(name)) {
            return "OpenViking Skill read failed: name is empty.";
        }
        try {
            OpenVikingReadResponse response = openVikingSkillService.readSkillMain(name);
            return formatReadResponse("read", name.trim(), "SKILL.md", response);
        } catch (IllegalArgumentException | OpenVikingClientException e) {
            log.warn("[OpenVikingSkillTool] Skill 主文件读取失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSkillTool] Skill 主文件读取异常", e);
            return "OpenViking Skill read failed: unexpected error.";
        }
    }

    @Tool("List the file tree for one uploaded OpenViking Skill. Use this when SKILL.md refers to supporting markdown files, examples, or references and you need the exact relative path before reading them. This tool only lists; it does not read file contents.")
    public String openviking_skill_files(
            @P("Exact Skill directory name under viking://agent/skills/, not a URI and not a file path.") String name
    ) {
        log.info("[OpenVikingSkillTool] openviking_skill_files name={}", name);
        if (!hasText(name)) {
            return "OpenViking Skill files failed: name is empty.";
        }
        try {
            OpenVikingReadResponse response = openVikingSkillService.listSkillFiles(name);
            return formatReadResponse("files", name.trim(), null, response);
        } catch (IllegalArgumentException | OpenVikingClientException e) {
            log.warn("[OpenVikingSkillTool] Skill 文件树读取失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSkillTool] Skill 文件树读取异常", e);
            return "OpenViking Skill files failed: unexpected error.";
        }
    }

    @Tool("Read one full supporting file inside an uploaded OpenViking Skill by exact Skill name and safe relative path. Use this only after SKILL.md or the Skill file tree shows the file is relevant. Do not use absolute paths, URIs, or ../ paths.")
    public String openviking_skill_read_file(
            @P("Exact Skill directory name under viking://agent/skills/, not a URI and not a file path.") String name,
            @P("Safe relative file path inside the Skill, such as README.md, references/research.md, or examples/demo.md. Do not start with / and do not include .. segments.") String path
    ) {
        log.info("[OpenVikingSkillTool] openviking_skill_read_file name={}, path={}", name, path);
        if (!hasText(name)) {
            return "OpenViking Skill file read failed: name is empty.";
        }
        if (!hasText(path)) {
            return "OpenViking Skill file read failed: path is empty.";
        }
        try {
            OpenVikingReadResponse response = openVikingSkillService.readSkillFile(name, path);
            return formatReadResponse("read_file", name.trim(), path.trim(), response);
        } catch (IllegalArgumentException | OpenVikingClientException e) {
            log.warn("[OpenVikingSkillTool] Skill 文件读取失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSkillTool] Skill 文件读取异常", e);
            return "OpenViking Skill file read failed: unexpected error.";
        }
    }

    private String formatFindResponse(String query, OpenVikingFindResponse response) {
        if (response == null || response.result() == null) {
            return "OpenViking Skill search failed: empty response.";
        }

        List<OpenVikingFindResponse.MatchedContext> results = new ArrayList<>();
        addAll(results, response.result().skills());
        addAll(results, response.result().resources());
        addAll(results, response.result().memories());

        if (results.isEmpty()) {
            return "OpenViking Skill search result\n"
                    + "query=" + query + "\n"
                    + "scope=viking://agent/skills/\n"
                    + "status=" + response.status() + "\n"
                    + "result=empty";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OpenViking Skill search results for: ").append(query).append("\n");
        sb.append("scope=viking://agent/skills/\n");
        sb.append("status=").append(response.status()).append("\n\n");

        int index = 1;
        for (OpenVikingFindResponse.MatchedContext context : results) {
            String item = formatContext(index++, context);
            if (sb.length() + item.length() > MAX_RESULT_CHARS) {
                sb.append("[truncated]\n");
                break;
            }
            sb.append(item);
        }

        Integer total = response.result().total();
        if (total != null) {
            sb.append("Total: ").append(total).append("\n");
        }
        return sb.toString().trim();
    }

    private String formatReadResponse(String operation, String name, String path, OpenVikingReadResponse response) {
        if (response == null) {
            return "OpenViking Skill " + operation + " failed: empty response.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("OpenViking Skill ").append(operation).append(" result\n");
        sb.append("name=").append(name).append("\n");
        if (hasText(path)) {
            sb.append("path=").append(path).append("\n");
        }
        sb.append("status=").append(response.status()).append("\n");
        sb.append("result=").append(formatResultValue(response.result()));
        return sb.toString();
    }

    private String formatContext(int index, OpenVikingFindResponse.MatchedContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Result ").append(index).append(":\n");
        if (context.score() != null) {
            sb.append("Score: ").append(String.format("%.3f", context.score())).append("\n");
        }
        if (hasText(context.uri())) {
            sb.append("URI: ").append(context.uri()).append("\n");
            String skillName = extractSkillName(context.uri());
            if (hasText(skillName)) {
                sb.append("Skill name: ").append(skillName).append("\n");
            }
        }
        if (hasText(context.contextType())) {
            sb.append("Type: ").append(context.contextType()).append("\n");
        }
        if (hasText(context.matchReason())) {
            sb.append("Reason: ").append(context.matchReason()).append("\n");
        }
        if (hasText(context.abstractText())) {
            sb.append("Content:\n").append(truncate(context.abstractText(), 1200)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String formatResultValue(Object result) {
        if (isEmptyResult(result)) {
            return "empty";
        }
        return "\n" + truncate(serializeResult(result), MAX_RESULT_CHARS);
    }

    private int sanitizeLimit(Integer limit) {
        int value = limit != null ? limit : DEFAULT_LIMIT;
        if (value < 1) {
            return 1;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private String validatePositiveLimit(String operation, String parameterName, Integer limit) {
        if (limit != null && limit < 1) {
            return "OpenViking Skill " + operation + " failed: " + parameterName + " must be a positive integer.";
        }
        return null;
    }

    private static boolean isEmptyResult(Object result) {
        if (result == null) {
            return true;
        }
        if (result instanceof String text) {
            return text.isBlank();
        }
        if (result instanceof List<?> list) {
            return list.isEmpty();
        }
        if (result instanceof Map<?, ?> map) {
            Object matches = map.get("matches");
            if (matches instanceof List<?> matchesList && matchesList.isEmpty()) {
                return true;
            }
            Object items = map.get("items");
            if (items instanceof List<?> itemsList && itemsList.isEmpty()) {
                return true;
            }
            Object results = map.get("results");
            return results instanceof List<?> resultsList && resultsList.isEmpty();
        }
        return false;
    }

    private static String serializeResult(Object result) {
        if (result instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return result.toString();
        }
    }

    private static String extractSkillName(String uri) {
        String prefix = "viking://agent/skills/";
        if (!hasText(uri) || !uri.startsWith(prefix)) {
            return null;
        }
        String rest = uri.substring(prefix.length());
        int slashIndex = rest.indexOf('/');
        return slashIndex >= 0 ? rest.substring(0, slashIndex) : rest;
    }

    private static void addAll(List<OpenVikingFindResponse.MatchedContext> target,
                               List<OpenVikingFindResponse.MatchedContext> source) {
        if (source != null && !source.isEmpty()) {
            target.addAll(source);
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
