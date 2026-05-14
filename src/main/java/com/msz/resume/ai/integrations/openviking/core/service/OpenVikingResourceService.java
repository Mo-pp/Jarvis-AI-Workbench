package com.msz.resume.ai.integrations.openviking.core.service;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.ResourceProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAddResourceRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingAddResourceResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingTempUploadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * OpenViking 长期资源库服务。
 *
 * <p>负责用户私有资源库的目录浏览、详情预览、文件上传、URL导入和纯文本创建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenVikingResourceService {

    public static final String RESOURCES_ROOT_URI = "viking://resources/";
    public static final String WORKSPACE_ROOT_URI = "viking://";
    private static final String RESOURCES_ROOT_URI_WITHOUT_TRAILING_SLASH = "viking://resources";
    private static final String WORKSPACE_ROOT_URI_WITHOUT_TRAILING_SLASH = "viking:";

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[^/\\\\:*?\"<>|\\x00-\\x1f]+$");
    private static final boolean DEFAULT_WAIT_FOR_PROCESSING = false;

    private final OpenVikingClient openVikingClient;
    private final ResourceProperties resourceProperties;

    // ==================== Phase 1: 目录浏览与详情预览 ====================

    /**
     * 列出指定目录下的资源。
     *
     * @param uri      目录URI，如 viking://resources/
     * @param identity 用户身份
     * @return 资源列表响应
     */
    public OpenVikingReadResponse listResources(String uri, OpenVikingIdentity identity) {
        String normalizedUri = normalizeListUri(uri);
        log.info("[OpenVikingResourceService] 列出资源目录: uri={}, identity={}", normalizedUri, identity);
        return openVikingClient.list(normalizedUri, null, identity);
    }

    /**
     * 从 OpenViking 根路径开始只读浏览工作空间。
     *
     * <p>该入口允许 viking:// 下所有当前身份可见的命名空间，写入和删除仍走资源库专用接口。</p>
     */
    public OpenVikingReadResponse listWorkspace(String uri, OpenVikingIdentity identity) {
        String normalizedUri = normalizeWorkspaceListUri(uri);
        log.info("[OpenVikingResourceService] 列出工作空间目录: uri={}, identity={}", normalizedUri, identity);
        return openVikingClient.list(normalizedUri, null, identity);
    }

    /**
     * 获取资源详情预览。
     *
     * @param uri      资源URI
     * @param identity 用户身份
     * @return 资源详情响应
     */
    public ResourceDetail getResourceDetail(String uri, OpenVikingIdentity identity) {
        String normalizedUri = normalizeResourceUri(uri);
        return getDetailForNormalizedUri(normalizedUri, identity);
    }

    /**
     * 获取工作空间内任意当前身份可见 URI 的只读详情。
     */
    public ResourceDetail getWorkspaceDetail(String uri, OpenVikingIdentity identity) {
        String normalizedUri = normalizeWorkspaceResourceUri(uri);
        return getDetailForNormalizedUri(normalizedUri, identity);
    }

    private ResourceDetail getDetailForNormalizedUri(String normalizedUri, OpenVikingIdentity identity) {
        log.info("[OpenVikingResourceService] 获取资源详情: uri={}", normalizedUri);

        // 先尝试获取概览（L1），用于目录和非Markdown文件
        String overview = tryReadOverview(normalizedUri, identity);
        String abstractText = tryReadAbstract(normalizedUri, identity);

        // 判断是否是Markdown文件
        boolean isMarkdownFile = isMarkdownFile(normalizedUri);

        ResourceDetailBuilder builder = ResourceDetailBuilder.create()
                .uri(normalizedUri)
                .name(extractName(normalizedUri))
                .abstractText(abstractText)
                .overviewText(overview);

        if (isMarkdownFile) {
            // Markdown文件：读取完整内容
            int previewLimit = resourceProperties.getMaxPreviewChars();
            String content = tryReadContent(normalizedUri, identity, previewLimit);
            builder.previewKind("markdown");
            builder.preview(truncatePreview(content, previewLimit));
            builder.directory(false);
            builder.type("file");
        } else if (isDirectory(normalizedUri)) {
            // 目录：使用概览作为预览
            builder.previewKind("directory");
            builder.preview(overview != null ? overview : abstractText);
            builder.directory(true);
            builder.type("directory");
        } else {
            // 其他文件：使用摘要
            builder.previewKind("abstract");
            builder.preview(abstractText);
            builder.directory(false);
            builder.type(detectFileType(normalizedUri));
        }

        return builder.build();
    }

    // ==================== Phase 2: 纯文本资源创建 ====================

    /**
     * 创建纯文本Markdown资源。
     *
     * @param name     资源文件名（不含扩展名或含.md扩展名）
     * @param content  资源正文
     * @param identity 用户身份
     * @return 创建结果
     */
    public CreateTextResourceResult createTextResource(String name, String content, OpenVikingIdentity identity) {
        // 参数校验
        validateTextResourceName(name);
        validateTextContent(content);

        // 生成目标URI
        String normalizedFilename = normalizeTextFilename(name);
        String targetUri = buildTextResourceUri(normalizedFilename);

        log.info("[OpenVikingResourceService] 创建纯文本资源: name={}, targetUri={}", normalizedFilename, targetUri);

        // 直接尝试写入，OpenViking 会在冲突时返回错误
        // 不进行预检查以避免 TOCTOU 竞态条件
        try {
            OpenVikingWriteRequest writeRequest = new OpenVikingWriteRequest(
                    targetUri,
                    content,
                    "create",  // 使用 create 模式，资源已存在时会返回错误
                    false,
                    null
            );
            OpenVikingWriteResponse writeResponse = openVikingClient.write(writeRequest, identity);

            if (writeResponse.error() != null) {
                String errorMessage = writeResponse.error().message();
                log.warn("[OpenVikingResourceService] 纯文本资源写入失败: uri={}, error={}", targetUri, errorMessage);

                // 检测冲突错误
                if (isConflictError(errorMessage)) {
                    return CreateTextResourceResult.conflict(name, targetUri);
                }
                return CreateTextResourceResult.failed(name, targetUri, errorMessage);
            }

            log.info("[OpenVikingResourceService] 纯文本资源创建成功: uri={}", targetUri);
            return CreateTextResourceResult.success(name, targetUri, extractRootUri(targetUri));
        } catch (Exception e) {
            log.error("[OpenVikingResourceService] 纯文本资源创建异常: uri={}", targetUri, e);
            if (isConflictError(e.getMessage())) {
                return CreateTextResourceResult.conflict(name, targetUri);
            }
            return CreateTextResourceResult.failed(name, targetUri, e.getMessage());
        }
    }

    /**
     * 检测是否是资源冲突错误。
     */
    private boolean isConflictError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        return lower.contains("already exists") ||
               lower.contains("conflict") ||
               lower.contains("duplicate") ||
               lower.contains("已存在");
    }

    // ==================== Phase 3: 文件上传资源导入 ====================

    /**
     * 上传文件资源。
     *
     * @param files    上传的文件列表
     * @param identity 用户身份
     * @return 每个文件的上传结果
     */
    public List<UploadResourceResult> uploadFiles(List<MultipartFile> files, OpenVikingIdentity identity) {
        List<UploadResourceResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            UploadResourceResult result = uploadSingleFile(file, identity);
            results.add(result);
        }

        return results;
    }

    private UploadResourceResult uploadSingleFile(MultipartFile file, OpenVikingIdentity identity) {
        String originalFilename = file.getOriginalFilename();

        // 参数校验：空文件
        if (file == null || file.isEmpty()) {
            return UploadResourceResult.failed(originalFilename, "文件为空");
        }
        // 参数校验：空文件名
        if (originalFilename == null || originalFilename.isBlank()) {
            return UploadResourceResult.failed(originalFilename, "文件名为空");
        }
        // 参数校验：文件名安全性
        try {
            validateUploadFilename(originalFilename);
        } catch (IllegalArgumentException e) {
            return UploadResourceResult.failed(originalFilename, e.getMessage());
        }
        // 参数校验：文件大小（显式校验，与 Spring multipart 配置对齐）
        long maxFileSize = resourceProperties.getMaxFileSizeBytes();
        if (file.getSize() > maxFileSize) {
            return UploadResourceResult.failed(originalFilename, "文件大小超过限制（最大" + (maxFileSize / 1024 / 1024) + "MB）");
        }

        // 生成目标URI
        String targetUri = buildFileResourceUri(originalFilename);
        String sourceName = extractSourceName(originalFilename);

        log.info("[OpenVikingResourceService] 上传文件: filename={}, size={}, targetUri={}",
                originalFilename, file.getSize(), targetUri);

        // 直接尝试上传，OpenViking 会在冲突时返回错误
        // 不进行预检查以避免 TOCTOU 竞态条件
        try {
            // 临时上传
            byte[] content = file.getBytes();
            OpenVikingTempUploadResponse tempUploadResponse = openVikingClient.tempUpload(
                    originalFilename,
                    content,
                    file.getContentType(),
                    identity
            );

            String tempFileId = tempUploadResponse.result() != null
                    ? tempUploadResponse.result().tempFileId()
                    : null;

            if (tempFileId == null || tempFileId.isBlank()) {
                return UploadResourceResult.failed(originalFilename, "临时上传未返回temp_file_id");
            }

            // 添加资源
            OpenVikingAddResourceRequest addRequest = OpenVikingAddResourceRequest.builder()
                    .tempFileId(tempFileId)
                    .to(targetUri)
                    .sourceName(sourceName)
                    .wait(DEFAULT_WAIT_FOR_PROCESSING)
                    .build();

            OpenVikingAddResourceResponse addResponse = openVikingClient.addResource(addRequest, identity);

            if (addResponse.error() != null) {
                String errorMessage = addResponse.error().message();
                log.warn("[OpenVikingResourceService] 文件资源添加失败: filename={}, error={}", originalFilename, errorMessage);

                // 检测冲突错误
                if (isConflictError(errorMessage)) {
                    return UploadResourceResult.conflict(originalFilename, targetUri);
                }
                return UploadResourceResult.failed(originalFilename, errorMessage);
            }

            String rootUri = addResponse.result() != null ? addResponse.result().rootUri() : extractRootUri(targetUri);
            log.info("[OpenVikingResourceService] 文件资源上传成功: filename={}, rootUri={}", originalFilename, rootUri);
            return UploadResourceResult.success(originalFilename, targetUri, rootUri);

        } catch (IOException e) {
            log.error("[OpenVikingResourceService] 文件读取失败: filename={}", originalFilename, e);
            return UploadResourceResult.failed(originalFilename, "文件读取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[OpenVikingResourceService] 文件上传异常: filename={}", originalFilename, e);
            if (isConflictError(e.getMessage())) {
                return UploadResourceResult.conflict(originalFilename, targetUri);
            }
            return UploadResourceResult.failed(originalFilename, e.getMessage());
        }
    }

    // ==================== Phase 4: URL资源导入 ====================

    /**
     * 通过URL导入资源。
     *
     * @param url      资源URL
     * @param identity 用户身份
     * @return 导入结果
     */
    public ImportUrlResult importFromUrl(String url, OpenVikingIdentity identity) {
        // 参数校验
        validateUrl(url);

        log.info("[OpenVikingResourceService] 导入URL资源: url={}", url);

        try {
            OpenVikingAddResourceRequest request = OpenVikingAddResourceRequest.builder()
                    .path(url)
                    .wait(DEFAULT_WAIT_FOR_PROCESSING)
                    .build();

            OpenVikingAddResourceResponse response = openVikingClient.addResource(request, identity);

            if (response.error() != null) {
                String errorMessage = response.error().message();
                log.warn("[OpenVikingResourceService] URL资源导入失败: url={}, error={}", url, errorMessage);
                return ImportUrlResult.failed(url, errorMessage);
            }

            String rootUri = response.result() != null ? response.result().rootUri() : null;
            String targetUri = response.result() != null ? response.result().uri() : null;
            log.info("[OpenVikingResourceService] URL资源导入成功: url={}, rootUri={}", url, rootUri);
            return ImportUrlResult.success(url, targetUri, rootUri);

        } catch (Exception e) {
            log.error("[OpenVikingResourceService] URL资源导入异常: url={}", url, e);
            return ImportUrlResult.failed(url, e.getMessage());
        }
    }

    // ==================== Phase 5: 资源删除 ====================

    /**
     * 删除长期资源库中的一个资源包。
     *
     * <p>前端可能传入资源包根目录，也可能传入资源包内的具体文件。
     * 删除时统一提升到 viking://resources/{name}/ 这一层，避免只删掉包内单个文件后留下摘要、索引或目录残留。</p>
     *
     * @param uri      选中的资源 URI
     * @param identity 用户身份
     * @return 删除结果
     */
    public DeleteResourceResult deleteResource(String uri, OpenVikingIdentity identity) {
        String deleteUri = resolveResourceDeleteUri(uri);
        log.info("[OpenVikingResourceService] 删除资源: requestedUri={}, deleteUri={}, identity={}", uri, deleteUri, identity);

        try {
            openVikingClient.remove(deleteUri, true, identity);
            return DeleteResourceResult.success(uri, deleteUri);
        } catch (Exception e) {
            log.error("[OpenVikingResourceService] 删除资源失败: requestedUri={}, deleteUri={}", uri, deleteUri, e);
            return DeleteResourceResult.failed(uri, deleteUri, e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    public String normalizeListUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return RESOURCES_ROOT_URI;
        }
        String normalized = uri.trim();
        if (!normalized.startsWith(RESOURCES_ROOT_URI)) {
            throw new IllegalArgumentException("URI must start with " + RESOURCES_ROOT_URI);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    public String normalizeWorkspaceListUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return WORKSPACE_ROOT_URI;
        }
        String normalized = uri.trim().replace('\\', '/');
        if (WORKSPACE_ROOT_URI_WITHOUT_TRAILING_SLASH.equals(normalized)) {
            return WORKSPACE_ROOT_URI;
        }
        if (!normalized.startsWith(WORKSPACE_ROOT_URI)) {
            throw new IllegalArgumentException("URI must start with " + WORKSPACE_ROOT_URI);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String normalizeResourceUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("URI is required");
        }
        String normalized = uri.trim().replace('\\', '/');
        if (RESOURCES_ROOT_URI_WITHOUT_TRAILING_SLASH.equals(normalized)) {
            return normalized;
        }
        if (!normalized.startsWith(RESOURCES_ROOT_URI)) {
            throw new IllegalArgumentException("URI must start with " + RESOURCES_ROOT_URI);
        }
        return normalized;
    }

    private String normalizeWorkspaceResourceUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("URI is required");
        }
        String normalized = uri.trim().replace('\\', '/');
        if (WORKSPACE_ROOT_URI_WITHOUT_TRAILING_SLASH.equals(normalized)) {
            return WORKSPACE_ROOT_URI;
        }
        if (!normalized.startsWith(WORKSPACE_ROOT_URI)) {
            throw new IllegalArgumentException("URI must start with " + WORKSPACE_ROOT_URI);
        }
        return normalized;
    }

    public String resolveResourceDeleteUri(String uri) {
        String normalized = normalizeResourceUri(uri);
        if (RESOURCES_ROOT_URI.equals(normalized) || RESOURCES_ROOT_URI_WITHOUT_TRAILING_SLASH.equals(normalized)) {
            throw new IllegalArgumentException("不能删除资源根目录");
        }

        String relativePath = normalized.substring(RESOURCES_ROOT_URI.length());
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("不能删除资源根目录");
        }

        String[] segments = relativePath.split("/");
        String rootName = null;
        int segmentCount = 0;
        for (String segment : segments) {
            if (!segment.isBlank()) {
                segmentCount++;
                if (rootName == null) {
                    rootName = segment;
                }
            }
        }
        if (rootName == null || rootName.isBlank()) {
            throw new IllegalArgumentException("资源 URI 无效");
        }

        boolean topLevelFile = segmentCount == 1 && !normalized.endsWith("/") && rootName.contains(".");
        if (topLevelFile) {
            return RESOURCES_ROOT_URI + rootName;
        }
        return RESOURCES_ROOT_URI + rootName + "/";
    }

    private String tryReadOverview(String uri, OpenVikingIdentity identity) {
        try {
            OpenVikingReadResponse response = openVikingClient.readOverview(uri, identity);
            return extractContent(response);
        } catch (Exception e) {
            log.debug("[OpenVikingResourceService] 读取概览失败: uri={}, error={}", uri, e.getMessage());
            return null;
        }
    }

    private String tryReadAbstract(String uri, OpenVikingIdentity identity) {
        try {
            OpenVikingReadResponse response = openVikingClient.readAbstract(uri, identity);
            return extractContent(response);
        } catch (Exception e) {
            log.debug("[OpenVikingResourceService] 读取摘要失败: uri={}, error={}", uri, e.getMessage());
            return null;
        }
    }

    private String tryReadContent(String uri, OpenVikingIdentity identity, int limit) {
        try {
            OpenVikingReadResponse response = openVikingClient.read(uri, identity);
            String content = extractContent(response);
            return truncatePreview(content, limit);
        } catch (Exception e) {
            log.debug("[OpenVikingResourceService] 读取内容失败: uri={}, error={}", uri, e.getMessage());
            return null;
        }
    }

    private String extractContent(OpenVikingReadResponse response) {
        if (response == null || response.result() == null) {
            return null;
        }
        Object result = response.result();
        if (result instanceof String text) {
            return text;
        }
        if (result instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content instanceof String text) {
                return text;
            }
        }
        return null;
    }

    private boolean resourceExists(String uri, OpenVikingIdentity identity) {
        try {
            openVikingClient.readAbstract(uri, identity);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractName(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String normalized = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    private boolean isMarkdownFile(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    private boolean isDirectory(String uri) {
        if (uri == null) {
            return false;
        }
        if (uri.endsWith("/")) {
            return true;
        }
        String normalized = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int lastSlash = normalized.lastIndexOf('/');
        String name = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        return !name.contains(".");
    }

    private String detectFileType(String uri) {
        if (uri == null) {
            return "unknown";
        }
        String lower = uri.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "word";
        if (lower.endsWith(".txt")) return "text";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js") || lower.endsWith(".ts")) return "javascript";
        return "file";
    }

    private String truncatePreview(String content, int limit) {
        if (content == null) {
            return null;
        }
        if (content.length() <= limit) {
            return content;
        }
        return content.substring(0, limit) + "...[truncated]";
    }

    // ==================== 文本资源相关辅助方法 ====================

    private void validateTextResourceName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("资源文件名不能为空");
        }
        String trimmed = name.trim();
        int maxFilenameLength = resourceProperties.getMaxFilenameLength();
        if (trimmed.length() > maxFilenameLength) {
            throw new IllegalArgumentException("资源文件名过长，最大" + maxFilenameLength + "字符");
        }
        // 拒绝路径遍历片段
        if (trimmed.equals(".") || trimmed.equals("..") || trimmed.contains("./") || trimmed.contains("../")) {
            throw new IllegalArgumentException("资源文件名不能包含路径遍历字符");
        }
        if (!VALID_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("资源文件名包含非法字符");
        }
    }

    private void validateTextContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("资源正文不能为空");
        }
        long maxBytes = resourceProperties.getMaxTextContentBytes();
        if (content.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("资源正文过大，最大" + (maxBytes / 1024 / 1024) + "MB");
        }
    }

    /**
     * 验证上传文件的文件名安全性。
     *
     * @param filename 原始文件名
     * @throws IllegalArgumentException 如果文件名不安全
     */
    private void validateUploadFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String trimmed = filename.trim();
        int maxFilenameLength = resourceProperties.getMaxFilenameLength();
        if (trimmed.length() > maxFilenameLength) {
            throw new IllegalArgumentException("文件名过长，最大" + maxFilenameLength + "字符");
        }
        // 拒绝路径遍历片段
        if (trimmed.equals(".") || trimmed.equals("..") ||
                trimmed.contains("/") || trimmed.contains("\\") ||
                trimmed.contains("./") || trimmed.contains(".\\") ||
                trimmed.contains("../") || trimmed.contains("..\\")) {
            throw new IllegalArgumentException("文件名不能包含路径分隔符或路径遍历字符");
        }
        // 拒绝特殊设备文件名
        String upperName = trimmed.toUpperCase(Locale.ROOT);
        if (upperName.equals("CON") || upperName.equals("PRN") || upperName.equals("AUX") ||
                upperName.equals("NUL") || upperName.startsWith("COM") || upperName.startsWith("LPT")) {
            throw new IllegalArgumentException("文件名不能是保留设备名");
        }
        // 检查非法字符
        if (!VALID_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("文件名包含非法字符");
        }
    }

    private String normalizeTextFilename(String name) {
        String trimmed = name.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".markdown")) {
            return trimmed + ".md";
        }
        return trimmed;
    }

    private String buildTextResourceUri(String filename) {
        // filename.md -> viking://resources/filename/filename.md
        String baseName = filename.replaceAll("(?i)\\.(md|markdown)$", "");
        return RESOURCES_ROOT_URI + baseName + "/" + filename;
    }

    private String buildFileResourceUri(String originalFilename) {
        // filename.ext -> viking://resources/filename/filename.ext
        String baseName = extractBaseName(originalFilename);
        return RESOURCES_ROOT_URI + baseName + "/" + originalFilename;
    }

    private String extractBaseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "untitled";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String extractSourceName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "untitled";
        }
        return filename;
    }

    private String extractRootUri(String targetUri) {
        if (targetUri == null) {
            return null;
        }
        // viking://resources/name/name.md -> viking://resources/name/
        int lastSlash = targetUri.lastIndexOf('/');
        if (lastSlash > 0) {
            int secondLastSlash = targetUri.lastIndexOf('/', lastSlash - 1);
            if (secondLastSlash > 0) {
                return targetUri.substring(0, secondLastSlash + 1);
            }
        }
        return targetUri;
    }

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL不能为空");
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // 检查是否是本地路径
        if (lower.startsWith("file:") ||
                lower.matches("^[a-z]:[\\\\/].*") ||
                trimmed.startsWith("/") ||
                trimmed.startsWith("~/") ||
                trimmed.startsWith("./") ||
                trimmed.startsWith("../")) {
            throw new IllegalArgumentException("不支持本地路径，请提供远程URL");
        }

        // 检查协议
        if (!lower.startsWith("http://") && !lower.startsWith("https://") &&
                !lower.startsWith("git://") && !lower.startsWith("git@") &&
                !lower.startsWith("ssh://")) {
            throw new IllegalArgumentException("URL协议不支持，仅支持HTTP(S)、Git、SSH协议");
        }
    }

    private OpenVikingIdentity currentIdentity() {
        return ToolRuntimeContext.getOpenVikingIdentity();
    }

    // ==================== 内部记录类 ====================

    public record ResourceDetail(
            String uri,
            String name,
            Long size,
            Boolean directory,
            String type,
            String updatedAt,
            String previewKind,
            String preview,
            String abstractText,
            String overviewText
    ) {}

    public static class ResourceDetailBuilder {
        private String uri;
        private String name;
        private Long size;
        private Boolean directory;
        private String type;
        private String updatedAt;
        private String previewKind;
        private String preview;
        private String abstractText;
        private String overviewText;

        public static ResourceDetailBuilder create() {
            return new ResourceDetailBuilder();
        }

        public ResourceDetailBuilder uri(String uri) { this.uri = uri; return this; }
        public ResourceDetailBuilder name(String name) { this.name = name; return this; }
        public ResourceDetailBuilder size(Long size) { this.size = size; return this; }
        public ResourceDetailBuilder directory(Boolean directory) { this.directory = directory; return this; }
        public ResourceDetailBuilder type(String type) { this.type = type; return this; }
        public ResourceDetailBuilder updatedAt(String updatedAt) { this.updatedAt = updatedAt; return this; }
        public ResourceDetailBuilder previewKind(String previewKind) { this.previewKind = previewKind; return this; }
        public ResourceDetailBuilder preview(String preview) { this.preview = preview; return this; }
        public ResourceDetailBuilder abstractText(String abstractText) { this.abstractText = abstractText; return this; }
        public ResourceDetailBuilder overviewText(String overviewText) { this.overviewText = overviewText; return this; }

        public ResourceDetail build() {
            return new ResourceDetail(uri, name, size, directory, type, updatedAt, previewKind, preview, abstractText, overviewText);
        }
    }

    public record CreateTextResourceResult(
            String sourceType,
            String sourceName,
            String targetUri,
            String rootUri,
            String status,
            String message
    ) {
        public static CreateTextResourceResult success(String name, String targetUri, String rootUri) {
            return new CreateTextResourceResult("text", name, targetUri, rootUri, "success", "资源创建成功");
        }

        public static CreateTextResourceResult conflict(String name, String targetUri) {
            return new CreateTextResourceResult("text", name, targetUri, null, "conflict", "资源已存在，请使用其他名称");
        }

        public static CreateTextResourceResult failed(String name, String targetUri, String error) {
            return new CreateTextResourceResult("text", name, targetUri, null, "failed", error);
        }
    }

    public record UploadResourceResult(
            String sourceType,
            String sourceName,
            String targetUri,
            String rootUri,
            String status,
            String message
    ) {
        public static UploadResourceResult success(String filename, String targetUri, String rootUri) {
            return new UploadResourceResult("file", filename, targetUri, rootUri, "success", "文件上传成功");
        }

        public static UploadResourceResult conflict(String filename, String targetUri) {
            return new UploadResourceResult("file", filename, targetUri, null, "conflict", "资源已存在，请使用其他文件名");
        }

        public static UploadResourceResult failed(String filename, String error) {
            return new UploadResourceResult("file", filename, null, null, "failed", error);
        }
    }

    public record ImportUrlResult(
            String sourceType,
            String sourceName,
            String targetUri,
            String rootUri,
            String status,
            String message
    ) {
        public static ImportUrlResult success(String url, String targetUri, String rootUri) {
            return new ImportUrlResult("url", url, targetUri, rootUri, "success", "资源导入请求已提交");
        }

        public static ImportUrlResult failed(String url, String error) {
            return new ImportUrlResult("url", url, null, null, "failed", error);
        }
    }

    public record DeleteResourceResult(
            String sourceType,
            String sourceName,
            String targetUri,
            String rootUri,
            String status,
            String message
    ) {
        public static DeleteResourceResult success(String requestedUri, String deletedUri) {
            return new DeleteResourceResult("resource", requestedUri, requestedUri, deletedUri, "success", "资源删除成功");
        }

        public static DeleteResourceResult failed(String requestedUri, String deleteUri, String error) {
            return new DeleteResourceResult("resource", requestedUri, requestedUri, deleteUri, "failed", error);
        }
    }
}
