package com.msz.resume.ai.integrations.openviking.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSearchResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.tool.CoreTool;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenViking 检索工具族（核心工具）。
 */
@Slf4j
@CoreTool
@Component
@RequiredArgsConstructor
public class OpenVikingSearchTool {

    private static final String LEVEL_ABSTRACT = "abstract";
    private static final String LEVEL_OVERVIEW = "overview";
    private static final String LEVEL_READ = "read";
    private static final String DEFAULT_URI = "viking://";
    private static final int MAX_PATTERN_LENGTH = 256;
    private static final int MAX_BROWSE_NODE_LIMIT = 1000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenVikingClient openVikingClient;
    private final OpenVikingProperties properties;

    @Tool("Read one OpenViking resource by URI and disclosure level. Works over authorized OpenViking namespaces such as resources, user, agent, and session; it is not limited to user memories. Use level=abstract for a short directory/resource summary, level=overview for directory/resource navigation, and level=read for exact file content. This is the final read step after list/tree/glob/grep/find/search identifies the URI.")
    public String openviking_read(
            @P("OpenViking URI to read. Use an authorized directory URI such as viking://user/{userId}/, viking://user/{userId}/memories/, viking://agent/{agentId}/, or viking://resources/ with level=abstract or level=overview; use an exact file URI such as viking://user/{userId}/profile.md or viking://resources/docs/file.md with level=read.") String uri,
            @P("Disclosure level: abstract, overview, or read.") String level
    ) {
        log.info("[OpenVikingSearchTool] openviking_read uri={}, level={}", uri, level);

        if (!hasText(uri)) {
            return "OpenViking read failed: uri is empty.";
        }
        if (!hasText(level)) {
            return "OpenViking read failed: level is empty.";
        }

        String normalizedLevel = level.trim().toLowerCase();
        if (!LEVEL_ABSTRACT.equals(normalizedLevel) && !LEVEL_OVERVIEW.equals(normalizedLevel) && !LEVEL_READ.equals(normalizedLevel)) {
            return "OpenViking read failed: level must be abstract, overview, or read.";
        }
        try {
            OpenVikingIdentity identity = currentIdentity();
            OpenVikingReadResponse response = switch (normalizedLevel) {
                case LEVEL_ABSTRACT -> openVikingClient.readAbstract(uri.trim(), identity);
                case LEVEL_OVERVIEW -> openVikingClient.readOverview(uri.trim(), identity);
                case LEVEL_READ -> openVikingClient.read(uri.trim(), identity);
                default -> throw new IllegalStateException("Unexpected level: " + normalizedLevel);
            };
            return formatReadResponse(uri.trim(), normalizedLevel, response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 读取失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 读取异常", e);
            return "OpenViking read failed: unexpected error.";
        }
    }

    @Tool("List direct children under an authorized OpenViking URI. Use this browse tool to inspect a scope before choosing what to read. Works over namespaces such as resources, user, agent, and session; it returns a flat child list, not a recursive tree and not semantic search results.")
    public String openviking_list(
            @P("OpenViking URI scope to list, for example viking://user/{userId}/, viking://user/{userId}/memories/, viking://agent/{agentId}/, viking://session/{sessionId}/, or viking://resources/.") String uri,
            @P(value = "Maximum number of nodes to return. Recommended 100 to 1000.", required = false) Integer nodeLimit
    ) {
        log.info("[OpenVikingSearchTool] openviking_list uri={}, nodeLimit={}", uri, nodeLimit);
        if (!hasText(uri)) {
            return "OpenViking list failed: uri is empty.";
        }
        int sanitizedNodeLimit = sanitizeNodeLimit(nodeLimit);
        try {
            OpenVikingReadResponse response = openVikingClient.list(uri.trim(), sanitizedNodeLimit, currentIdentity());
            return formatBrowseResponse("list", uri.trim(), sanitizedNodeLimit, response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 列表浏览失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 列表浏览异常", e);
            return "OpenViking list failed: unexpected error.";
        }
    }

    @Tool("Return a hierarchical OpenViking tree for an authorized URI scope. Use this browse tool when nested structure matters. Works over namespaces such as resources, user, agent, and session. Do not use it for content matching or semantic retrieval.")
    public String openviking_tree(
            @P("OpenViking URI scope to render as a tree, for example viking://resources/, viking://user/{userId}/, or viking://agent/{agentId}/.") String uri,
            @P(value = "Maximum number of nodes to return. Recommended 100 to 1000.", required = false) Integer nodeLimit
    ) {
        log.info("[OpenVikingSearchTool] openviking_tree uri={}, nodeLimit={}", uri, nodeLimit);
        if (!hasText(uri)) {
            return "OpenViking tree failed: uri is empty.";
        }
        int sanitizedNodeLimit = sanitizeNodeLimit(nodeLimit);
        try {
            OpenVikingReadResponse response = openVikingClient.tree(uri.trim(), sanitizedNodeLimit, currentIdentity());
            return formatBrowseResponse("tree", uri.trim(), sanitizedNodeLimit, response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 层级浏览失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 层级浏览异常", e);
            return "OpenViking tree failed: unexpected error.";
        }
    }

    @Tool("Find OpenViking URIs by path or filename pattern. Use this narrow tool when you know a path shape or filename pattern. It is not semantic search and does not inspect file content.")
    public String openviking_glob(
            @P("Glob pattern to match paths or filenames, for example **/*.md or *memory*.") String pattern,
            @P(value = "OpenViking URI scope to search under. Defaults to viking:// when omitted.", required = false) String uri,
            @P(value = "Maximum number of matching URIs to return. Recommended 5 to 20.", required = false) Integer nodeLimit
    ) {
        log.info("[OpenVikingSearchTool] openviking_glob pattern={}, uri={}, nodeLimit={}", pattern, uri, nodeLimit);
        if (!hasText(pattern)) {
            return "OpenViking glob failed: pattern is empty. Provide a glob pattern such as **/*.md.";
        }
        String normalizedPattern = pattern.trim();
        if (normalizedPattern.length() > MAX_PATTERN_LENGTH) {
            return "OpenViking glob failed: pattern is too long. Keep it under 256 characters.";
        }
        String normalizedUri = hasText(uri) ? uri.trim() : DEFAULT_URI;
        if (!isVikingUri(normalizedUri)) {
            return "OpenViking glob failed: uri must start with viking://.";
        }
        String limitError = validatePositiveLimit("glob", "nodeLimit", nodeLimit);
        if (limitError != null) {
            return limitError;
        }
        int sanitizedNodeLimit = sanitizeNarrowLimit(nodeLimit);
        try {
            OpenVikingReadResponse response = openVikingClient.glob(normalizedPattern, normalizedUri, sanitizedNodeLimit, currentIdentity());
            return formatNarrowResponse("glob", normalizedUri, normalizedPattern, sanitizedNodeLimit, response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 路径匹配失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 路径匹配异常", e);
            return "OpenViking glob failed: unexpected error.";
        }
    }

    @Tool("Find OpenViking resources whose content matches a text pattern. Use this narrow tool to locate candidate URIs by literal or pattern content before reading. It is not semantic retrieval.")
    public String openviking_grep(
            @P("OpenViking URI scope to search under, for example viking://resources/.") String uri,
            @P("Text or pattern to match in resource content.") String pattern,
            @P(value = "Whether matching should ignore case. Defaults to false.", required = false) Boolean caseInsensitive,
            @P(value = "Maximum number of matches to return. Recommended 5 to 20.", required = false) Integer nodeLimit
    ) {
        log.info("[OpenVikingSearchTool] openviking_grep uri={}, pattern={}, caseInsensitive={}, nodeLimit={}", uri, pattern, caseInsensitive, nodeLimit);
        if (!hasText(uri)) {
            return "OpenViking grep failed: uri is empty. Provide an authorized scope such as viking://resources/.";
        }
        String normalizedUri = uri.trim();
        if (!isVikingUri(normalizedUri)) {
            return "OpenViking grep failed: uri must start with viking://.";
        }
        if (!hasText(pattern)) {
            return "OpenViking grep failed: pattern is empty. Provide text or a regular expression to match.";
        }
        String normalizedPattern = pattern.trim();
        if (normalizedPattern.length() > MAX_PATTERN_LENGTH) {
            return "OpenViking grep failed: pattern is too long. Keep it under 256 characters.";
        }
        String limitError = validatePositiveLimit("grep", "nodeLimit", nodeLimit);
        if (limitError != null) {
            return limitError;
        }
        int sanitizedNodeLimit = sanitizeNarrowLimit(nodeLimit);
        try {
            OpenVikingReadResponse response = openVikingClient.grep(normalizedUri, normalizedPattern, caseInsensitive, sanitizedNodeLimit, currentIdentity());
            return formatNarrowResponse("grep", normalizedUri, normalizedPattern, sanitizedNodeLimit, response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 内容匹配失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 内容匹配异常", e);
            return "OpenViking grep failed: unexpected error.";
        }
    }

    @Tool("Run ordinary OpenViking query-centric semantic retrieval. Use openviking_find when the query should stand alone without current conversation/session context. Do not use it when the result must be biased by current session context; use openviking_search for that.")
    public String openviking_find(
            @P("Search query, usually the user's question or key terms.") String query,
            @P(value = "Optional target Viking URI scope, for example viking://resources/, viking://user/{userId}/, viking://agent/{agentId}/skills/, or viking://session/{sessionId}/. Leave blank to search all indexed content visible to the current identity.", required = false) String targetUri,
            @P(value = "Maximum number of results. Recommended 3 to 5.", required = false) Integer limit
    ) {
        log.info("[OpenVikingSearchTool] openviking_find query={}, targetUri={}, limit={}", query, targetUri, limit);

        if (query == null || query.isBlank()) {
            return "OpenViking find failed: query is empty.";
        }

        String limitError = validatePositiveLimit("find", "limit", limit);
        if (limitError != null) {
            return limitError;
        }
        int sanitizedLimit = sanitizeLimit(limit);
        OpenVikingFindRequest request = new OpenVikingFindRequest(
                query,
                hasText(targetUri) ? targetUri.trim() : null,
                sanitizedLimit,
                sanitizedLimit,
                null,
                true
        );

        try {
            OpenVikingFindResponse response = openVikingClient.find(request, currentIdentity());
            return formatFindResponse(query, response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 检索失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 检索异常", e);
            return "OpenViking find failed: unexpected error.";
        }
    }

    @Tool("Run current-session-aware OpenViking semantic retrieval. Use this only when current conversation/session context must affect retrieval. The backend binds the current session automatically; do not provide or invent session_id.")
    public String openviking_search(
            @P("Search query, usually the user's question or key terms.") String query,
            @P(value = "Optional target Viking URI scope, for example viking://resources/, viking://user/{userId}/skills/, or viking://agent/{agentId}/skills/. Leave blank to search all indexed content visible to the current identity. Do not provide viking://session/{sessionId}/ to force session binding; the backend receives the current session automatically.", required = false) String targetUri,
            @P(value = "Maximum number of results. Recommended 3 to 5.", required = false) Integer limit
    ) {
        log.info("[OpenVikingSearchTool] openviking_search query={}, targetUri={}, limit={}", query, targetUri, limit);
        if (query == null || query.isBlank()) {
            return "OpenViking search failed: query is empty.";
        }

        String limitError = validatePositiveLimit("search", "limit", limit);
        if (limitError != null) {
            return limitError;
        }

        String sessionId = ToolRuntimeContext.getSessionId();
        if (!hasText(sessionId)) {
            return "OpenViking search failed: current session is not bound.";
        }

        int sanitizedLimit = sanitizeLimit(limit);
        OpenVikingSearchRequest request = new OpenVikingSearchRequest(
                query,
                hasText(targetUri) ? targetUri.trim() : null,
                sessionId,
                sanitizedLimit,
                sanitizedLimit,
                null,
                null,
                true,
                null,
                null,
                null
        );

        try {
            OpenVikingSearchResponse response = openVikingClient.search(request, currentIdentity());
            return formatSearchResponse(query, response);
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 会话感知检索失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 会话感知检索异常", e);
            return "OpenViking search failed: unexpected error.";
        }
    }

    @Tool("Permanently delete one exact OpenViking viking:// URI. This is irreversible. Use only when the user explicitly asks to forget, delete, or remove something. First use openviking_find, openviking_search, openviking_glob, openviking_grep, or openviking_list to identify the exact URI and confirm it with the user. Do not delete by query. Set confirmed=true only after the user has confirmed this exact URI.")
    public String openviking_forget(
            @P("Exact OpenViking URI to permanently delete. Must start with viking://. Do not pass a search query.") String uri,
            @P("Must be true only after the user explicitly confirms deleting this exact URI.") Boolean confirmed
    ) {
        log.info("[OpenVikingSearchTool] openviking_forget uri={}, confirmed={}", uri, confirmed);
        if (!hasText(uri)) {
            return "OpenViking forget failed: uri is empty.";
        }
        String normalizedUri = uri.trim();
        if (!isVikingUri(normalizedUri)) {
            return "OpenViking forget failed: uri must start with viking://.";
        }
        if (!Boolean.TRUE.equals(confirmed)) {
            return "OpenViking forget failed: deletion is irreversible and requires confirmed=true after the user confirms the exact URI.";
        }
        try {
            openVikingClient.remove(normalizedUri, false, currentIdentity());
            return "OpenViking forget result\n"
                    + "uri=" + normalizedUri + "\n"
                    + "status=deleted\n"
                    + "recursive=false";
        } catch (OpenVikingClientException e) {
            log.warn("[OpenVikingSearchTool] 删除失败: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            log.error("[OpenVikingSearchTool] 删除异常", e);
            return "OpenViking forget failed: unexpected error.";
        }
    }

    private String formatReadResponse(String uri, String level, OpenVikingReadResponse response) {
        if (response == null) {
            return "OpenViking read failed: empty response.";
        }
        Object result = response.result();
        return "OpenViking read result\n"
                + "uri=" + uri + "\n"
                + "level=" + level + "\n"
                + "status=" + response.status() + "\n"
                + "result=" + formatResultValue(result);
    }

    private String formatBrowseResponse(String operation, String uri, int nodeLimit, OpenVikingReadResponse response) {
        if (response == null) {
            return "OpenViking " + operation + " failed: empty response.";
        }
        Object result = response.result();
        return "OpenViking " + operation + " result\n"
                + "uri=" + uri + "\n"
                + "node_limit=" + nodeLimit + "\n"
                + "status=" + response.status() + "\n"
                + "result=" + formatResultValue(result);
    }

    private String formatNarrowResponse(String operation, String uri, String pattern, int nodeLimit, OpenVikingReadResponse response) {
        if (response == null) {
            return "OpenViking " + operation + " failed: empty response.";
        }
        Object result = response.result();
        return "OpenViking " + operation + " result\n"
                + "uri=" + uri + "\n"
                + "pattern=" + pattern + "\n"
                + "node_limit=" + nodeLimit + "\n"
                + "status=" + response.status() + "\n"
                + "result=" + formatResultValue(result);
    }

    private String formatSearchResponse(String query, OpenVikingSearchResponse response) {
        if (response == null || response.result() == null) {
            return "OpenViking search failed: empty response.";
        }

        List<OpenVikingFindResponse.MatchedContext> results = new ArrayList<>();
        addAll(results, response.result().memories());
        addAll(results, response.result().resources());
        addAll(results, response.result().skills());

        if (results.isEmpty()) {
            return "OpenViking search result\n"
                    + "query=" + query + "\n"
                    + "session_bound=true\n"
                    + "status=" + response.status() + "\n"
                    + "result=empty";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OpenViking session-aware search results for: ").append(query).append("\n");
        sb.append("session_bound=true\n");
        sb.append("status=").append(response.status()).append("\n\n");

        int index = 1;
        for (OpenVikingFindResponse.MatchedContext context : results) {
            String item = formatContext(index++, context);
            if (sb.length() + item.length() > properties.getMaxResultChars()) {
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

    private String formatFindResponse(String query, OpenVikingFindResponse response) {
        if (response == null || response.result() == null) {
            return "OpenViking find failed: empty response.";
        }

        List<OpenVikingFindResponse.MatchedContext> results = new ArrayList<>();
        addAll(results, response.result().memories());
        addAll(results, response.result().resources());
        addAll(results, response.result().skills());

        if (results.isEmpty()) {
            return "OpenViking find result\n"
                    + "query=" + query + "\n"
                    + "status=" + response.status() + "\n"
                    + "result=empty";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OpenViking find results for: ").append(query).append("\n");
        sb.append("status=").append(response.status()).append("\n\n");

        int index = 1;
        for (OpenVikingFindResponse.MatchedContext context : results) {
            String item = formatContext(index++, context);
            if (sb.length() + item.length() > properties.getMaxResultChars()) {
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

    private String formatContext(int index, OpenVikingFindResponse.MatchedContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Result ").append(index).append(":\n");
        if (context.score() != null) {
            sb.append("Score: ").append(String.format("%.3f", context.score())).append("\n");
        }
        if (hasText(context.uri())) {
            sb.append("URI: ").append(context.uri()).append("\n");
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

    private int sanitizeLimit(Integer limit) {
        int value = limit != null ? limit : properties.getDefaultLimit();
        if (value < 1) {
            return 1;
        }
        return Math.min(value, properties.getMaxLimit());
    }

    private OpenVikingIdentity currentIdentity() {
        return ToolRuntimeContext.getOpenVikingIdentity();
    }

    private String formatResultValue(Object result) {
        if (isEmptyResult(result)) {
            return "empty";
        }
        return "\n" + truncate(serializeResult(result), properties.getMaxResultChars());
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
            if (results instanceof List<?> resultsList && resultsList.isEmpty()) {
                return true;
            }
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

    private int sanitizeNodeLimit(Integer nodeLimit) {
        int value = nodeLimit != null ? nodeLimit : MAX_BROWSE_NODE_LIMIT;
        if (value < 1) {
            return 1;
        }
        return Math.min(value, MAX_BROWSE_NODE_LIMIT);
    }

    private int sanitizeNarrowLimit(Integer nodeLimit) {
        int value = nodeLimit != null ? nodeLimit : properties.getDefaultLimit();
        if (value < 1) {
            return 1;
        }
        return Math.min(value, properties.getMaxLimit());
    }

    private String validatePositiveLimit(String operation, String parameterName, Integer limit) {
        if (limit != null && limit < 1) {
            return "OpenViking " + operation + " failed: " + parameterName + " must be a positive integer.";
        }
        return null;
    }

    private static boolean isVikingUri(String uri) {
        return uri != null && uri.startsWith("viking://");
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
