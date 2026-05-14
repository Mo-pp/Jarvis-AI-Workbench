package com.msz.resume.ai.integrations.openviking.api;

import com.msz.resume.ai.auth.entity.Account;
import com.msz.resume.ai.auth.support.CurrentAccountResolver;
import com.msz.resume.ai.shared.response.Result;
import com.msz.resume.ai.integrations.openviking.api.dto.ResourceDetailResponse;
import com.msz.resume.ai.integrations.openviking.api.dto.ResourceImportResultResponse;
import com.msz.resume.ai.integrations.openviking.api.dto.ResourceItemResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingIdentityResolver;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.CreateTextResourceResult;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.DeleteResourceResult;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.ImportUrlResult;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.ResourceDetail;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingResourceService.UploadResourceResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 长期资源库控制器。
 *
 * <p>提供用户私有资源库的目录浏览、详情预览、文件上传、URL导入和纯文本创建接口。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final CurrentAccountResolver currentAccountResolver;
    private final OpenVikingIdentityResolver openVikingIdentityResolver;
    private final OpenVikingResourceService openVikingResourceService;

    /**
     * 列出指定目录下的资源。
     *
     * @param httpServletRequest HTTP请求
     * @param uri                目录URI，默认为 viking://resources/
     * @return 资源列表
     */
    @GetMapping
    public Result<List<ResourceItemResponse>> listResources(
            HttpServletRequest httpServletRequest,
            @RequestParam(required = false) String uri) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources");
            log.info("[ResourceController] 列出资源目录: username={}, uri={}", context.account().getUsername(), uri);

            String currentUri = openVikingResourceService.normalizeListUri(uri);
            OpenVikingReadResponse response = openVikingResourceService.listResources(
                    currentUri,
                    context.identity()
            );

            List<ResourceItemResponse> items = extractResourceItems(response, currentUri);
            Result<List<ResourceItemResponse>> result = Result.success(items);
            result.setMsg("success");
            return result;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] 资源目录请求参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] 资源目录查询失败", e);
            return Result.error("资源目录查询失败: " + e.getMessage());
        }
    }

    /**
     * 只读浏览 OpenViking 工作空间，从 viking:// 根目录开始。
     */
    @GetMapping("/workspace")
    public Result<List<ResourceItemResponse>> listWorkspace(
            HttpServletRequest httpServletRequest,
            @RequestParam(required = false) String uri) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources/workspace");
            log.info("[ResourceController] 列出工作空间目录: username={}, uri={}", context.account().getUsername(), uri);

            String currentUri = openVikingResourceService.normalizeWorkspaceListUri(uri);
            OpenVikingReadResponse response = openVikingResourceService.listWorkspace(
                    currentUri,
                    context.identity()
            );

            List<ResourceItemResponse> items = extractResourceItems(response, currentUri);
            Result<List<ResourceItemResponse>> result = Result.success(items);
            result.setMsg("success");
            return result;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] 工作空间目录请求参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] 工作空间目录查询失败", e);
            return Result.error("工作空间目录查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取资源详情预览。
     *
     * @param httpServletRequest HTTP请求
     * @param uri                资源URI
     * @return 资源详情
     */
    @GetMapping("/detail")
    public Result<ResourceDetailResponse> getResourceDetail(
            HttpServletRequest httpServletRequest,
            @RequestParam String uri) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources/detail");
            log.info("[ResourceController] 获取资源详情: username={}, uri={}", context.account().getUsername(), uri);

            ResourceDetail detail = openVikingResourceService.getResourceDetail(uri, context.identity());

            ResourceDetailResponse response = toResourceDetailResponse(detail);

            Result<ResourceDetailResponse> result = Result.success(response);
            result.setMsg("success");
            return result;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] 资源详情请求参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] 资源详情查询失败", e);
            return Result.error("资源详情查询失败: " + e.getMessage());
        }
    }

    /**
     * 只读获取工作空间内文件或目录的预览详情。
     */
    @GetMapping("/workspace/detail")
    public Result<ResourceDetailResponse> getWorkspaceDetail(
            HttpServletRequest httpServletRequest,
            @RequestParam String uri) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources/workspace/detail");
            log.info("[ResourceController] 获取工作空间详情: username={}, uri={}", context.account().getUsername(), uri);

            ResourceDetail detail = openVikingResourceService.getWorkspaceDetail(uri, context.identity());
            ResourceDetailResponse response = toResourceDetailResponse(detail);

            Result<ResourceDetailResponse> result = Result.success(response);
            result.setMsg("success");
            return result;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] 工作空间详情请求参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] 工作空间详情查询失败", e);
            return Result.error("工作空间详情查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建纯文本Markdown资源。
     *
     * @param httpServletRequest HTTP请求
     * @param request            创建请求
     * @return 创建结果
     */
    @PostMapping("/text")
    public Result<ResourceImportResultResponse> createTextResource(
            HttpServletRequest httpServletRequest,
            @RequestBody CreateTextResourceRequest request) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources/text");
            log.info("[ResourceController] 创建纯文本资源: username={}, name={}",
                    context.account().getUsername(), request.name());

            CreateTextResourceResult result = openVikingResourceService.createTextResource(
                    request.name(),
                    request.content(),
                    context.identity()
            );

            ResourceImportResultResponse response = toImportResultResponse(result);
            Result<ResourceImportResultResponse> apiResult = Result.success(response);
            apiResult.setMsg(result.status().equals("success") ? "创建成功" : result.message());
            return apiResult;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] 纯文本资源创建参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] 纯文本资源创建失败", e);
            return Result.error("纯文本资源创建失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件资源。
     *
     * @param httpServletRequest HTTP请求
     * @param files              上传的文件列表
     * @return 每个文件的上传结果
     */
    @PostMapping("/upload")
    public Result<List<ResourceImportResultResponse>> uploadFiles(
            HttpServletRequest httpServletRequest,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources/upload");
            log.info("[ResourceController] 上传文件资源: username={}, fileCount={}",
                    context.account().getUsername(), files != null ? files.size() : 0);

            if (files == null || files.isEmpty()) {
                return Result.error("请选择要上传的文件");
            }

            List<UploadResourceResult> results = openVikingResourceService.uploadFiles(files, context.identity());

            List<ResourceImportResultResponse> responses = new ArrayList<>();
            for (UploadResourceResult result : results) {
                responses.add(toUploadResultResponse(result));
            }

            Result<List<ResourceImportResultResponse>> apiResult = Result.success(responses);
            apiResult.setMsg("文件上传完成");
            return apiResult;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] 文件上传参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] 文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 通过URL导入资源。
     *
     * @param httpServletRequest HTTP请求
     * @param request            导入请求
     * @return 导入结果
     */
    @PostMapping("/url")
    public Result<ResourceImportResultResponse> importFromUrl(
            HttpServletRequest httpServletRequest,
            @RequestBody ImportUrlRequest request) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources/url");
            log.info("[ResourceController] 导入URL资源: username={}, url={}",
                    context.account().getUsername(), request.url());

            ImportUrlResult result = openVikingResourceService.importFromUrl(request.url(), context.identity());

            ResourceImportResultResponse response = toImportUrlResultResponse(result);
            Result<ResourceImportResultResponse> apiResult = Result.success(response);
            apiResult.setMsg(result.status().equals("success") ? "导入请求已提交" : result.message());
            return apiResult;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] URL导入参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] URL导入失败", e);
            return Result.error("URL导入失败: " + e.getMessage());
        }
    }

    /**
     * 删除资源包。
     *
     * @param httpServletRequest HTTP请求
     * @param uri                资源URI，可以是资源根目录或资源包内具体文件
     * @return 删除结果
     */
    @DeleteMapping
    public Result<ResourceImportResultResponse> deleteResource(
            HttpServletRequest httpServletRequest,
            @RequestParam String uri) {
        try {
            ResolvedRequestContext context = resolveRequestContext(httpServletRequest, "resources/delete");
            log.info("[ResourceController] 删除资源: username={}, uri={}",
                    context.account().getUsername(), uri);

            DeleteResourceResult result = openVikingResourceService.deleteResource(uri, context.identity());

            ResourceImportResultResponse response = toDeleteResultResponse(result);
            Result<ResourceImportResultResponse> apiResult = Result.success(response);
            apiResult.setMsg(result.status().equals("success") ? "删除成功" : result.message());
            return apiResult;

        } catch (IllegalArgumentException e) {
            log.warn("[ResourceController] 资源删除参数非法: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ResourceController] 资源删除失败", e);
            return Result.error("资源删除失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private List<ResourceItemResponse> extractResourceItems(OpenVikingReadResponse response, String parentUri) {
        List<ResourceItemResponse> items = new ArrayList<>();
        if (response == null || response.result() == null) {
            return items;
        }

        Object result = response.result();
        if (result instanceof List<?> list) {
            for (Object item : list) {
                ResourceItemResponse itemResponse = toResourceItemResponse(item, parentUri);
                if (itemResponse != null) {
                    items.add(itemResponse);
                }
            }
        } else if (result instanceof Map<?, ?> map) {
            Object itemList = firstValue(map, "items", "entries", "children", "files");
            if (itemList instanceof List<?> list) {
                for (Object item : list) {
                    ResourceItemResponse itemResponse = toResourceItemResponse(item, parentUri);
                    if (itemResponse != null) {
                        items.add(itemResponse);
                    }
                }
            }
        }

        return items;
    }

    private ResourceDetailResponse toResourceDetailResponse(ResourceDetail detail) {
        return ResourceDetailResponse.builder()
                .uri(detail.uri())
                .name(detail.name())
                .size(detail.size())
                .directory(detail.directory())
                .type(detail.type())
                .updatedAt(detail.updatedAt())
                .previewKind(detail.previewKind())
                .preview(detail.preview())
                .abstractText(detail.abstractText())
                .overviewText(detail.overviewText())
                .build();
    }

    private ResourceItemResponse toResourceItemResponse(Object item, String parentUri) {
        if (item == null) {
            return null;
        }

        if (item instanceof String value) {
            String uri = resolveItemUri(value, parentUri, looksLikeDirectory(null, value));
            if (uri == null) {
                return null;
            }
            Boolean directory = inferDirectory(null, uri, value);
            return ResourceItemResponse.builder()
                    .uri(uri)
                    .name(extractName(uri))
                    .directory(directory)
                    .type(Boolean.TRUE.equals(directory) ? "directory" : "file")
                    .build();
        }

        if (item instanceof Map<?, ?> map) {
            String rawUri = asString(map.get("uri"), map.get("path"), map.get("rel_path"), map.get("relativePath"));
            String name = asString(map.get("name"));
            Boolean directory = inferDirectory(map, rawUri, name);
            String uri = resolveItemUri(rawUri != null ? rawUri : name, parentUri, directory);
            if (uri == null) {
                return null;
            }
            Long size = asLong(firstValue(map, "size", "bytes", "file_size", "fileSize"));
            String type = asString(map.get("type"), map.get("kind"), map.get("node_type"), map.get("nodeType"));
            if (type == null) {
                type = Boolean.TRUE.equals(directory) ? "directory" : "file";
            }
            String updatedAt = asString(map.get("updated_at"), map.get("updatedAt"), map.get("mtime"), map.get("modTime"), map.get("modified"), map.get("last_modified"), map.get("lastModified"));
            String abstractText = asString(map.get("abstract"), map.get("abstractText"), map.get("summary"));

            if (name == null && uri != null) {
                name = extractName(uri);
            }

            return ResourceItemResponse.builder()
                    .uri(uri)
                    .name(name)
                    .size(size)
                    .directory(directory)
                    .type(type)
                    .updatedAt(updatedAt)
                    .abstractText(abstractText)
                    .build();
        }

        return null;
    }

    private Object firstValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String resolveItemUri(String rawValue, String parentUri, Boolean directory) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String value = rawValue.trim();
        String uri;
        if (value.startsWith("viking://")) {
            uri = value;
        } else {
            String parent = parentUri == null || parentUri.isBlank()
                    ? OpenVikingResourceService.RESOURCES_ROOT_URI
                    : parentUri.trim();
            parent = parent.endsWith("/") ? parent : parent + "/";
            String child = value;
            while (child.startsWith("/")) {
                child = child.substring(1);
            }
            if (child.startsWith("resources/")) {
                uri = OpenVikingResourceService.RESOURCES_ROOT_URI + child.substring("resources/".length());
            } else {
                uri = parent + child;
            }
        }

        if (Boolean.TRUE.equals(directory) && !uri.endsWith("/")) {
            return uri + "/";
        }
        if (Boolean.FALSE.equals(directory) && uri.endsWith("/")) {
            return uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    private Boolean inferDirectory(Map<?, ?> map, String rawUri, String name) {
        if (map != null) {
            Boolean explicit = asBoolean(
                    map.get("directory"),
                    map.get("is_dir"),
                    map.get("isDir"),
                    map.get("dir")
            );
            if (explicit != null) {
                return explicit;
            }

            String type = asString(map.get("type"), map.get("kind"), map.get("node_type"), map.get("nodeType"));
            if (type != null) {
                String lowerType = type.toLowerCase(Locale.ROOT);
                if (lowerType.contains("dir") || lowerType.contains("folder")) {
                    return true;
                }
                if (lowerType.contains("file")) {
                    return false;
                }
            }

            Boolean isLeaf = asBoolean(map.get("is_leaf"), map.get("isLeaf"));
            if (isLeaf != null) {
                return !isLeaf;
            }

            Long mode = asLong(map.get("mode"));
            if (mode != null) {
                return (mode & 040000) == 040000;
            }
        }

        return looksLikeDirectory(rawUri, name);
    }

    private Boolean looksLikeDirectory(String rawUri, String name) {
        String value = rawUri != null && !rawUri.isBlank() ? rawUri.trim() : name;
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.endsWith("/")) {
            return true;
        }
        String leaf = extractName(value);
        return leaf != null && !leaf.contains(".");
    }

    private String extractName(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String normalized = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private String asString(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean asBoolean(Object... values) {
        for (Object value : values) {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value instanceof Number n) {
                return n.intValue() != 0;
            }
            if (value instanceof String s && !s.isBlank()) {
                String lower = s.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower)) {
                    return true;
                }
                if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower)) {
                    return false;
                }
            }
        }
        return null;
    }

    private ResourceImportResultResponse toImportResultResponse(CreateTextResourceResult result) {
        return ResourceImportResultResponse.builder()
                .sourceType(result.sourceType())
                .sourceName(result.sourceName())
                .targetUri(result.targetUri())
                .rootUri(result.rootUri())
                .status(result.status())
                .message(result.message())
                .build();
    }

    private ResourceImportResultResponse toUploadResultResponse(UploadResourceResult result) {
        return ResourceImportResultResponse.builder()
                .sourceType(result.sourceType())
                .sourceName(result.sourceName())
                .targetUri(result.targetUri())
                .rootUri(result.rootUri())
                .status(result.status())
                .message(result.message())
                .build();
    }

    private ResourceImportResultResponse toImportUrlResultResponse(ImportUrlResult result) {
        return ResourceImportResultResponse.builder()
                .sourceType(result.sourceType())
                .sourceName(result.sourceName())
                .targetUri(result.targetUri())
                .rootUri(result.rootUri())
                .status(result.status())
                .message(result.message())
                .build();
    }

    private ResourceImportResultResponse toDeleteResultResponse(DeleteResourceResult result) {
        return ResourceImportResultResponse.builder()
                .sourceType(result.sourceType())
                .sourceName(result.sourceName())
                .targetUri(result.targetUri())
                .rootUri(result.rootUri())
                .status(result.status())
                .message(result.message())
                .build();
    }

    private ResolvedRequestContext resolveRequestContext(HttpServletRequest request, String endpoint) {
        Account currentAccount = currentAccountResolver.requireCurrentAccount(request, endpoint);
        OpenVikingIdentity identity = openVikingIdentityResolver.resolve(currentAccount);
        return new ResolvedRequestContext(currentAccount, identity);
    }

    private record ResolvedRequestContext(
            Account account,
            OpenVikingIdentity identity
    ) {
    }

    // ==================== 请求体DTO ====================

    public record CreateTextResourceRequest(
            String name,
            String content
    ) {
    }

    public record ImportUrlRequest(
            String url
    ) {
    }
}
