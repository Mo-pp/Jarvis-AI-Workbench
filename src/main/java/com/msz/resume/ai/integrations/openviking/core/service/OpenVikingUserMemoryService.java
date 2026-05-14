package com.msz.resume.ai.integrations.openviking.core.service;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenViking 用户 Markdown 记忆服务。
 *
 * <p>JARVIS 只写入 L2 原始记忆文件；L0/L1 由 OpenViking 根据 memory context 自动维护：</p>
 * <pre>
 * viking://user/{userId}/memories/.abstract.md
 * viking://user/{userId}/memories/.overview.md
 * viking://user/{userId}/memories/{type}_{key}.md
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenVikingUserMemoryService {

    private static final String WRITE_MODE_CREATE = "create";
    private static final String WRITE_MODE_REPLACE = "replace";
    private static final boolean WAIT_FOR_POST_PROCESSING = false;
    private static final Double WRITE_WAIT_TIMEOUT_SECONDS = null;
    private static final int MAX_LOADED_MEMORIES = 3;
    private static final int MAX_SINGLE_MEMORY_CHARS = 1500;
    private static final int MAX_MEMORY_SECTION_CHARS = 4000;
    private static final List<String> MEMORY_TYPES = List.of("user", "feedback", "project", "reference");
    private static final Pattern INDEX_LINE_PATTERN = Pattern.compile("^\\s*- \\[(.+?)]\\(((?:user|feedback|project|reference)_[a-zA-Z0-9_-]+\\.md)\\)\\s+—\\s+(.+)$");
    private static final Pattern MEMORY_FILENAME_PATTERN = Pattern.compile("^(user|feedback|project|reference)_[a-zA-Z0-9_-]+\\.md$");
    private static final Pattern MEMORY_FRONTMATTER_PATTERN = Pattern.compile("(?s)^---\\R(.*?)\\R---\\R?(.*)$");
    private static final Pattern FRONTMATTER_LINE_PATTERN = Pattern.compile("^([a-zA-Z0-9_-]+):\\s*(.*)$");

    private final OpenVikingClient openVikingClient;

    public String buildMemoryIndexUri(String userId) {
        validateRequiredText(userId, "userId");
        return "viking://user/" + userId.trim() + "/memories/MEMORY.md";
    }

    public String buildMemoryLayerUri(String userId, String layerFilename) {
        validateRequiredText(userId, "userId");
        validateRequiredText(layerFilename, "layerFilename");
        String normalizedLayerFilename = layerFilename.trim();
        if (!".abstract.md".equals(normalizedLayerFilename) && !".overview.md".equals(normalizedLayerFilename)) {
            throw new IllegalArgumentException("OpenViking user memory operation failed: layerFilename must be .abstract.md or .overview.md.");
        }
        return "viking://user/" + userId.trim() + "/memories/" + normalizedLayerFilename;
    }

    public String buildMemoryFileUri(String userId, String filename) {
        validateRequiredText(userId, "userId");
        validateMemoryFilename(filename);
        return "viking://user/" + userId.trim() + "/memories/" + filename.trim();
    }

    public String readMemoryIndex(String userId) {
        String indexUri = buildMemoryIndexUri(userId);
        OpenVikingReadResponse response = openVikingClient.read(indexUri, currentIdentity());
        return readResultAsText(response);
    }

    public OpenVikingWriteResponse writeMemoryFile(String userId, String filename, String markdown) {
        validateRequiredText(markdown, "markdown");
        String fileUri = buildMemoryFileUri(userId, filename);
        log.info("[OpenVikingUserMemoryService] 写入用户 Markdown 记忆: userId={}, filename={}, uri={}",
                userId, filename, fileUri);
        return writeWithCreateOrReplace(fileUri, markdown.trim());
    }

    public SaveMemoryResult saveMemory(String userId, String type, String key,
                                       String name, String description, String content) {
        validateRequiredText(userId, "userId");
        validateRequiredText(type, "type");
        validateRequiredText(key, "key");
        validateRequiredText(name, "name");
        validateRequiredText(description, "description");
        validateRequiredText(content, "content");

        String sanitizedUserId = userId.trim();
        String normalizedType = normalizeMemoryType(type);
        String canonicalKey = canonicalizeMemoryKey(key);
        String normalizedName = normalizeInlineText(name);
        String normalizedDescription = normalizeInlineText(description);
        String normalizedContent = content.trim();
        String filename = buildMemoryFilename(normalizedType, canonicalKey);
        String markdown = buildMemoryMarkdown(normalizedType, canonicalKey, normalizedName, normalizedDescription, normalizedContent);

        OpenVikingWriteResponse response = writeMemoryFile(sanitizedUserId, filename, markdown);

        return new SaveMemoryResult(
                normalizedType,
                filename,
                buildMemoryFileUri(sanitizedUserId, filename),
                responseStatus(response),
                true,
                "OpenViking L0(.abstract.md)->L1(.overview.md)->L2(detail)"
        );
    }

    public void appendOrUpdateIndex(String userId, String type, String title, String filename, String description) {
        validateRequiredText(title, "title");
        validateRequiredText(description, "description");
        validateMemoryFilename(filename);

        String sanitizedUserId = userId.trim();
        String normalizedType = normalizeMemoryType(type);
        String indexUri = buildMemoryIndexUri(sanitizedUserId);
        IndexReadResult indexReadResult = readMemoryIndexOrDefaultWithExistsFlag(sanitizedUserId);
        MemoryIndexEntry newEntry = new MemoryIndexEntry(
                normalizeInlineText(title),
                filename.trim(),
                normalizeInlineText(description),
                normalizedType
        );
        String updatedIndex = appendOrReplaceIndexLine(indexReadResult.content(), newEntry);
        String writeMode = indexReadResult.exists() ? WRITE_MODE_REPLACE : WRITE_MODE_CREATE;

        log.info("[OpenVikingUserMemoryService] 更新用户记忆索引: userId={}, filename={}, uri={}, mode={}",
                sanitizedUserId, filename, indexUri, writeMode);
        writeWithBusyRetry(indexUri, updatedIndex, writeMode);
    }

    public String loadPromptMemoryOverview(String userId) {
        if (!hasText(userId)) {
            return "";
        }

        String sanitizedUserId = userId.trim();
        return readMemoryLayerForPrompt(sanitizedUserId, ".abstract.md", "L0 Abstract")
                .map(layer -> limitText(buildPromptMemoryOverviewSection(List.of(layer)), MAX_MEMORY_SECTION_CHARS))
                .orElse("");
    }

    private Optional<MemoryLayerContent> readMemoryLayerForPrompt(String userId, String layerFilename, String title) {
        try {
            String uri = buildMemoryLayerUri(userId, layerFilename);
            OpenVikingReadResponse response = openVikingClient.read(uri, currentIdentity());
            String content = readResultAsText(response);
            if (!hasText(content)) {
                return Optional.empty();
            }
            return Optional.of(new MemoryLayerContent(title, layerFilename, limitText(content.trim(), MAX_SINGLE_MEMORY_CHARS)));
        } catch (Exception e) {
            log.info("[OpenVikingUserMemoryService] OpenViking 用户记忆层读取失败，跳过: userId={}, layer={}, message={}",
                    userId, layerFilename, e.getMessage());
            return Optional.empty();
        }
    }

    String buildPromptMemoryOverviewSection(List<MemoryLayerContent> layers) {
        StringBuilder builder = new StringBuilder();
        builder.append("## Memory\n\n");
        builder.append("OpenViking exposes authorized URI namespaces with progressive disclosure. Default context includes only the user memory L0 abstract. ")
                .append("For user memory tasks, solve the problem instead of stopping at the first weak signal. ")
                .append("Start with `openviking_list` or `openviking_tree` on the user memory directory URI (`viking://user/{userId}/memories/`) when you need scope, structure, navigation, or candidate filenames. ")
                .append("Use directory URI (`viking://user/{userId}/memories/`) and level `overview` when you need a directory-level summary. ")
                .append("Use `openviking_grep` when you need to locate candidate memory files by literal content or keywords, and use `openviking_glob` when path shape or filename pattern is known. ")
                .append("Use `openviking_find` or `openviking_search` for semantic lookup, but an empty semantic result does not prove the memory does not exist. ")
                .append("Use `openviking_read` only after you already know the exact file URI, or when you intentionally need `abstract` or `overview` as a secondary summary signal. ")
                .append("If directory `openviking_read` returns HTML, a wrapper page, empty summary, or any clearly unrelated payload, treat that as an unreliable retrieval mode and immediately switch tools instead of concluding that no memory exists. ")
                .append("For non-memory OpenViking data, start from an authorized URI scope such as `viking://user/{userId}/`, `viking://agent/{agentId}/`, `viking://session/{sessionId}/`, or `viking://resources/` instead of assuming everything is under memories. ")
                .append("If exact details are needed, read the exact L2 file URI such as `viking://user/{userId}/memories/{type}_{key}.md` and level `read` with `openviking_read`.\n\n");
        for (MemoryLayerContent layer : layers) {
            builder.append("### ")
                    .append(layer.title())
                    .append(" (`")
                    .append(layer.filename())
                    .append("`)\n\n")
                    .append(layer.content())
                    .append("\n\n");
        }
        builder.append("Do not read L1 or L2 unless the user's request needs that additional memory context, but also do not stop early when the current retrieval path is obviously unreliable.");
        return builder.toString().trim();
    }

    public String readMemoryOverview(String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("OpenViking user memory operation failed: userId is blank.");
        }
        String overviewUri = buildMemoryLayerUri(userId.trim(), ".overview.md");
        OpenVikingReadResponse response = openVikingClient.read(overviewUri, currentIdentity());
        String content = readResultAsText(response);
        return limitText(content == null ? "" : content.trim(), MAX_MEMORY_SECTION_CHARS);
    }

    public String readMemoryDetail(String userId, String filename) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("OpenViking user memory operation failed: userId is blank.");
        }
        validateMemoryFilename(filename);
        String fileUri = buildMemoryFileUri(userId.trim(), filename.trim());
        OpenVikingReadResponse response = openVikingClient.read(fileUri, currentIdentity());
        String content = readResultAsText(response);
        return limitText(content == null ? "" : content.trim(), MAX_SINGLE_MEMORY_CHARS);
    }

    public String loadPromptMemories(String userId) {
        if (!hasText(userId)) {
            return "";
        }

        try {
            String index = readMemoryIndex(userId.trim());
            if (!hasText(index)) {
                return "";
            }

            List<MemoryIndexEntry> entries = parseMemoryIndex(index);
            if (entries.isEmpty()) {
                return "";
            }

            List<LoadedMemory> loadedMemories = new ArrayList<>();
            for (MemoryIndexEntry entry : entries) {
                if (loadedMemories.size() >= MAX_LOADED_MEMORIES) {
                    break;
                }
                readMemoryFileForPrompt(userId.trim(), entry)
                        .ifPresent(loadedMemories::add);
            }

            if (loadedMemories.isEmpty()) {
                return "";
            }

            return limitText(buildPromptMemorySection(loadedMemories), MAX_MEMORY_SECTION_CHARS);
        } catch (Exception e) {
            log.warn("[OpenVikingUserMemoryService] 加载用户 Markdown 记忆失败, userId={}, message={}",
                    userId, e.getMessage());
            return "";
        }
    }

    Optional<LoadedMemory> readMemoryFileForPrompt(String userId, MemoryIndexEntry entry) {
        try {
            String fileUri = buildMemoryFileUri(userId, entry.filename());
            OpenVikingReadResponse response = openVikingClient.read(fileUri, currentIdentity());
            String content = readResultAsText(response);
            if (!hasText(content)) {
                return Optional.empty();
            }
            MemoryDocument document = parseMemoryDocument(entry, content.trim());
            return Optional.of(new LoadedMemory(
                    document.entry(),
                    limitText(document.content(), MAX_SINGLE_MEMORY_CHARS)
            ));
        } catch (Exception e) {
            log.warn("[OpenVikingUserMemoryService] 跳过读取失败的用户记忆文件, userId={}, filename={}, message={}",
                    userId, entry.filename(), e.getMessage());
            return Optional.empty();
        }
    }

    String buildPromptMemorySection(List<LoadedMemory> loadedMemories) {
        StringBuilder builder = new StringBuilder();
        builder.append("## Memory\n\n");
        builder.append("The following memories persist across conversations. Apply them when relevant, unless the current user request explicitly overrides them.\n\n");
        builder.append("### Memory Index\n\n");
        for (LoadedMemory memory : loadedMemories) {
            MemoryIndexEntry entry = memory.entry();
            builder.append("- [")
                    .append(entry.type())
                    .append("] ")
                    .append(entry.title())
                    .append(" (`")
                    .append(entry.filename())
                    .append("`) — ")
                    .append(entry.description())
                    .append('\n');
        }
        builder.append("\n### Relevant Memories\n\n");
        for (LoadedMemory memory : loadedMemories) {
            MemoryIndexEntry entry = memory.entry();
            builder.append("Memory: ")
                    .append(entry.filename())
                    .append("\nType: ")
                    .append(entry.type())
                    .append("\nDescription: ")
                    .append(entry.description())
                    .append("\n\n")
                    .append(memory.content())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    List<MemoryIndexEntry> parseMemoryIndex(String index) {
        List<MemoryIndexEntry> entries = new ArrayList<>();
        String[] lines = index.split("\\R");
        for (String line : lines) {
            Matcher matcher = INDEX_LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String filename = matcher.group(2).trim();
            if (!isValidMemoryFilename(filename)) {
                continue;
            }
            entries.add(new MemoryIndexEntry(
                    normalizeInlineText(matcher.group(1)),
                    filename,
                    normalizeInlineText(matcher.group(3)),
                    extractTypeFromFilename(filename)
            ));
        }
        return dedupePreferNew(entries);
    }

    private List<MemoryIndexEntry> dedupePreferNew(List<MemoryIndexEntry> entries) {
        Map<String, MemoryIndexEntry> deduped = new LinkedHashMap<>();
        for (MemoryIndexEntry entry : entries) {
            String dedupeKey = entry.type() + ":" + entry.title().toLowerCase(Locale.ROOT);
            MemoryIndexEntry existing = deduped.get(dedupeKey);
            if (existing == null || (isLegacyFeedbackFilename(existing.filename()) && !isLegacyFeedbackFilename(entry.filename()))) {
                deduped.put(dedupeKey, entry);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private IndexReadResult readMemoryIndexOrDefaultWithExistsFlag(String userId) {
        try {
            String index = readMemoryIndex(userId);
            if (hasText(index)) {
                return new IndexReadResult(index.trim(), true);
            }
        } catch (Exception e) {
            log.info("[OpenVikingUserMemoryService] 用户记忆索引不存在或读取失败，将创建新索引: userId={}, message={}",
                    userId, e.getMessage());
        }
        return new IndexReadResult("# User Memory Index\n", false);
    }

    String appendOrReplaceIndexLine(String indexContent, MemoryIndexEntry newEntry) {
        List<MemoryIndexEntry> entries = parseMemoryIndex(indexContent);
        boolean replaced = false;
        List<MemoryIndexEntry> updatedEntries = new ArrayList<>();
        for (MemoryIndexEntry entry : entries) {
            if (entry.filename().equals(newEntry.filename())) {
                if (!replaced) {
                    updatedEntries.add(newEntry);
                    replaced = true;
                }
            } else {
                updatedEntries.add(entry);
            }
        }
        if (!replaced) {
            updatedEntries.add(newEntry);
        }
        return renderMemoryIndex(updatedEntries);
    }

    String renderMemoryIndex(List<MemoryIndexEntry> entries) {
        StringBuilder builder = new StringBuilder("# User Memory Index\n");
        for (String type : MEMORY_TYPES) {
            builder.append("\n## ").append(toSectionTitle(type)).append("\n");
            for (MemoryIndexEntry entry : entries) {
                if (!type.equals(entry.type())) {
                    continue;
                }
                builder.append("- [")
                        .append(entry.title())
                        .append("](")
                        .append(entry.filename())
                        .append(") — ")
                        .append(entry.description())
                        .append('\n');
            }
        }
        return builder.toString().trim() + '\n';
    }

    String buildMemoryMarkdown(String type, String key, String name, String description, String content) {
        String normalizedType = normalizeMemoryType(type);
        String canonicalKey = canonicalizeMemoryKey(key);
        return "---\n"
                + "name: " + quoteYaml(name) + "\n"
                + "description: " + quoteYaml(description) + "\n"
                + "type: " + normalizedType + "\n"
                + "key: " + canonicalKey + "\n"
                + "schema_version: 1\n"
                + "---\n\n"
                + content.trim()
                + "\n";
    }

    String buildMemoryFilename(String type, String key) {
        return normalizeMemoryType(type) + "_" + canonicalizeMemoryKey(key) + ".md";
    }

    String canonicalizeMemoryKey(String key) {
        validateRequiredText(key, "key");
        String canonical = key.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[_-]+|[_-]+$", "");
        if (!hasText(canonical)) {
            throw new IllegalArgumentException("OpenViking user memory operation failed: key cannot be canonicalized.");
        }
        return canonical;
    }

    private MemoryDocument parseMemoryDocument(MemoryIndexEntry fallbackEntry, String rawContent) {
        Matcher matcher = MEMORY_FRONTMATTER_PATTERN.matcher(rawContent);
        if (!matcher.matches()) {
            return new MemoryDocument(fallbackEntry, rawContent.trim());
        }

        Map<String, String> metadata = parseFrontmatter(matcher.group(1));
        String type = metadata.containsKey("type") ? normalizeMemoryType(metadata.get("type")) : fallbackEntry.type();
        String title = hasText(metadata.get("name")) ? normalizeInlineText(unquoteYaml(metadata.get("name"))) : fallbackEntry.title();
        String description = hasText(metadata.get("description"))
                ? normalizeInlineText(unquoteYaml(metadata.get("description")))
                : fallbackEntry.description();
        MemoryIndexEntry documentEntry = new MemoryIndexEntry(title, fallbackEntry.filename(), description, type);
        return new MemoryDocument(documentEntry, matcher.group(2).trim());
    }

    private Map<String, String> parseFrontmatter(String frontmatter) {
        Map<String, String> metadata = new LinkedHashMap<>();
        String[] lines = frontmatter.split("\\R");
        for (String line : lines) {
            Matcher matcher = FRONTMATTER_LINE_PATTERN.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            metadata.put(matcher.group(1), matcher.group(2).trim());
        }
        return metadata;
    }

    private OpenVikingWriteResponse writeWithCreateOrReplace(String uri, String content) {
        try {
            return writeWithBusyRetry(uri, content, WRITE_MODE_CREATE);
        } catch (OpenVikingClientException e) {
            if (shouldFallbackToReplace(e)) {
                if (verifyWrittenContent(uri, content, WRITE_MODE_CREATE)) {
                    log.info("[OpenVikingUserMemoryService] 文件已存在且内容一致，按成功处理: uri={}", uri);
                    return verifiedByReadResponse(uri, WRITE_MODE_CREATE);
                }
                log.info("[OpenVikingUserMemoryService] 文件已存在，回退为 replace 模式: uri={}", uri);
                return writeWithBusyRetry(uri, content, WRITE_MODE_REPLACE);
            }
            throw e;
        }
    }

    private OpenVikingWriteResponse writeWithMode(String uri, String content, String mode) {
        OpenVikingWriteRequest request = new OpenVikingWriteRequest(
                uri,
                content,
                mode,
                WAIT_FOR_POST_PROCESSING,
                WRITE_WAIT_TIMEOUT_SECONDS
        );
        try {
            return openVikingClient.write(request, currentIdentity());
        } catch (OpenVikingClientException e) {
            if (shouldVerifyWriteByRead(e) && verifyWrittenContent(uri, content, mode)) {
                log.info("[OpenVikingUserMemoryService] OpenViking 写入响应异常但 read-after-write 验证成功，按成功处理: uri={}, mode={}",
                        uri, mode);
                return verifiedByReadResponse(uri, mode);
            }
            throw e;
        }
    }

    private OpenVikingWriteResponse verifiedByReadResponse(String uri, String mode) {
        return new OpenVikingWriteResponse(
                "ok",
                new OpenVikingWriteResponse.Result(uri, mode, "verified_by_read", null, "Write response was uncertain, but read-after-write verification succeeded."),
                null,
                null
        );
    }

    private OpenVikingWriteResponse writeWithBusyRetry(String uri, String content, String mode) {
        OpenVikingClientException lastException = null;
        long[] retryDelaysMillis = {0L, 500L, 1_500L};
        for (int attempt = 0; attempt < retryDelaysMillis.length; attempt++) {
            long delayMillis = retryDelaysMillis[attempt];
            sleepBeforeRetry(delayMillis, uri);
            try {
                return writeWithMode(uri, content, mode);
            } catch (OpenVikingClientException e) {
                if (!isResourceBusy(e)) {
                    throw e;
                }
                if (verifyWrittenContent(uri, content, mode)) {
                    log.info("[OpenVikingUserMemoryService] OpenViking 资源忙但 read-after-write 验证成功，按成功处理: uri={}, mode={}, attempt={}/{}",
                            uri, mode, attempt + 1, retryDelaysMillis.length);
                    return verifiedByReadResponse(uri, mode);
                }
                lastException = e;
                log.info("[OpenVikingUserMemoryService] OpenViking 资源忙，准备重试写入: uri={}, mode={}, attempt={}/{}, waitedMillis={}, message={}",
                        uri, mode, attempt + 1, retryDelaysMillis.length, delayMillis, e.getMessage());
            }
        }
        if (lastException != null && verifyWrittenContent(uri, content, mode)) {
            log.info("[OpenVikingUserMemoryService] OpenViking 资源忙重试耗尽但 read-after-write 验证成功，按成功处理: uri={}, mode={}",
                    uri, mode);
            return verifiedByReadResponse(uri, mode);
        }
        log.warn("[OpenVikingUserMemoryService] OpenViking 资源忙重试耗尽: uri={}, mode={}, attempts={}, message={}",
                uri, mode, retryDelaysMillis.length, lastException != null ? lastException.getMessage() : "unknown");
        throw lastException;
    }

    private void sleepBeforeRetry(long delayMillis, String uri) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenVikingClientException("OpenViking write retry interrupted: " + uri, e);
        }
    }

    private boolean isResourceBusy(OpenVikingClientException e) {
        String message = e.getMessage();
        return hasText(message) && message.toLowerCase(Locale.ROOT).contains("resource is busy");
    }

    private boolean shouldVerifyWriteByRead(OpenVikingClientException e) {
        String message = e.getMessage();
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("response parse failed")
                || normalized.contains("error while extracting response")
                || normalized.contains("content type [application/octet-stream]")
                || normalized.contains("resource is busy")
                || normalized.contains("deadline_exceeded")
                || normalized.contains("deadline exceeded")
                || normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("504");
    }

    private boolean verifyWrittenContent(String uri, String expectedContent, String mode) {
        try {
            OpenVikingReadResponse response = openVikingClient.read(uri, currentIdentity());
            String actualContent = readResultAsText(response).trim();
            boolean matched = hasText(actualContent) && actualContent.equals(expectedContent.trim());
            log.info("[OpenVikingUserMemoryService] read-after-write 验证结果: uri={}, mode={}, matched={}, expectedChars={}, actualChars={}",
                    uri, mode, matched, expectedContent.length(), actualContent.length());
            return matched;
        } catch (Exception readException) {
            log.warn("[OpenVikingUserMemoryService] read-after-write 验证失败: uri={}, mode={}, message={}",
                    uri, mode, readException.getMessage());
            return false;
        }
    }

    private boolean shouldFallbackToReplace(OpenVikingClientException e) {
        String message = e.getMessage();
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("already exists")
                || normalized.contains("file exists")
                || normalized.contains("conflict")
                || normalized.contains("409");
    }

    private String responseStatus(OpenVikingWriteResponse response) {
        return response != null ? response.status() : "unknown";
    }

    private String readResultAsText(OpenVikingReadResponse response) {
        if (response == null || response.result() == null) {
            return "";
        }
        return response.result().toString();
    }

    private String limitText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }

    private String normalizeInlineText(String value) {
        validateRequiredText(value, "value");
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeMemoryType(String type) {
        validateRequiredText(type, "type");
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!MEMORY_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("OpenViking user memory operation failed: type must be one of user, feedback, project, reference.");
        }
        return normalized;
    }

    private String extractTypeFromFilename(String filename) {
        validateMemoryFilename(filename);
        return filename.substring(0, filename.indexOf('_')).toLowerCase(Locale.ROOT);
    }

    private String toSectionTitle(String type) {
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1);
    }

    private String quoteYaml(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String unquoteYaml(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("''", "'");
        }
        return trimmed;
    }

    private void validateMemoryFilename(String filename) {
        validateRequiredText(filename, "filename");
        if (!isValidMemoryFilename(filename.trim())) {
            throw new IllegalArgumentException("OpenViking user memory operation failed: filename must match {type}_{key}.md.");
        }
    }

    boolean isValidMemoryFilename(String filename) {
        return filename != null && MEMORY_FILENAME_PATTERN.matcher(filename).matches();
    }

    boolean isLegacyFeedbackFilename(String filename) {
        return filename != null && filename.matches("^feedback_[a-fA-F0-9]{16}\\.md$");
    }

    private void validateRequiredText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("OpenViking user memory operation failed: " + fieldName + " is blank.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private OpenVikingIdentity currentIdentity() {
        return ToolRuntimeContext.getOpenVikingIdentity();
    }

    public record SaveMemoryResult(
            String type,
            String filename,
            String fileUri,
            String status,
            boolean memorySaved,
            String readStrategy
    ) {
    }

    record MemoryLayerContent(
            String title,
            String filename,
            String content
    ) {
    }

    record MemoryIndexEntry(
            String title,
            String filename,
            String description,
            String type
    ) {
    }

    record LoadedMemory(
            MemoryIndexEntry entry,
            String content
    ) {
    }

    private record MemoryDocument(
            MemoryIndexEntry entry,
            String content
    ) {
    }

    private record IndexReadResult(
            String content,
            boolean exists
    ) {
    }
}
