package com.msz.resume.ai.integrations.openviking.core.service;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingTempUploadResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import com.msz.resume.ai.tool.ToolRuntimeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * OpenViking Skill 服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenVikingSkillService {

    private static final String LEGACY_SKILL_ROOT_URI = "viking://agent/skills/";
    private static final String AGENT_URI_PREFIX = "viking://agent/";
    private static final String SKILLS_SEGMENT = "/skills/";
    private static final Pattern SAFE_SKILL_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final boolean WAIT_FOR_POST_PROCESSING = false;

    private final OpenVikingClient openVikingClient;

    public OpenVikingReadResponse listSkills() {
        return listSkills(currentIdentity());
    }

    public OpenVikingReadResponse listSkills(OpenVikingIdentity identity) {
        String rootUri = buildSkillRootUri(identity);
        log.info("[OpenVikingSkillService] 列出 Skill 目录: uri={}", rootUri);
        return openVikingClient.list(rootUri, null, identity);
    }

    public List<SkillCatalogItem> listSkillCatalog(OpenVikingIdentity identity) {
        OpenVikingReadResponse listResponse = listSkills(identity);
        List<SkillCatalogSeed> seeds = extractSkillCatalogSeeds(listResponse, identity);
        List<SkillCatalogItem> items = new ArrayList<>();
        for (SkillCatalogSeed seed : seeds) {
            items.add(buildSkillCatalogItem(seed.id(), seed.name(), seed.path(), seed.updatedAt(), identity));
        }
        return items;
    }

    public SkillCatalogItem getSkillCatalogItem(String id, OpenVikingIdentity identity) {
        String skillId = validateSkillName(id);
        return buildSkillCatalogItem(skillId, skillId, buildSkillDirectoryUri(skillId, identity), null, identity);
    }

    public OpenVikingSkillAddResponse addSkill(Object data) {
        return addSkill(data, currentIdentity());
    }

    public OpenVikingSkillAddResponse addSkill(Object data, OpenVikingIdentity identity) {
        validateSkillData(data);
        log.info("[OpenVikingSkillService] 添加 Skill: source=data, dataType={}", data.getClass().getSimpleName());
        return openVikingClient.addSkill(new OpenVikingSkillAddRequest(
                data,
                null,
                WAIT_FOR_POST_PROCESSING,
                null
        ), identity);
    }

    public OpenVikingSkillAddResponse uploadSkill(MultipartFile file) throws IOException {
        return uploadSkill(file, currentIdentity());
    }

    public OpenVikingSkillAddResponse uploadSkill(MultipartFile file, OpenVikingIdentity identity) throws IOException {
        validateUpload(file);
        log.info("[OpenVikingSkillService] 上传 Skill 文件: filename={}, size={}", file.getOriginalFilename(), file.getSize());
        byte[] content = normalizeZipIfNeeded(file);
        OpenVikingTempUploadResponse uploadResponse = openVikingClient.tempUpload(
                file.getOriginalFilename(),
                content,
                file.getContentType(),
                identity
        );
        String tempFileId = uploadResponse.result() != null ? uploadResponse.result().tempFileId() : null;
        if (tempFileId == null || tempFileId.isBlank()) {
            throw new IllegalStateException("OpenViking temp upload did not return temp_file_id.");
        }
        return openVikingClient.addSkill(new OpenVikingSkillAddRequest(
                null,
                tempFileId,
                WAIT_FOR_POST_PROCESSING,
                null
        ), identity);
    }

    public Map<String, Object> deleteSkill(String name) {
        return deleteSkill(name, currentIdentity());
    }

    public Map<String, Object> deleteSkill(String name, OpenVikingIdentity identity) {
        String skillName = validateSkillName(name);
        String uri = buildSkillDirectoryUri(skillName, identity);
        log.info("[OpenVikingSkillService] 删除 Skill: name={}, uri={}", skillName, uri);
        openVikingClient.remove(uri, true, identity);
        return Map.of(
                "name", skillName,
                "uri", uri,
                "recursive", true
        );
    }

    public OpenVikingFindResponse searchSkills(String query, Integer limit) {
        return searchSkills(query, limit, currentIdentity());
    }

    public OpenVikingFindResponse searchSkills(String query, Integer limit, OpenVikingIdentity identity) {
        String normalizedQuery = validateSearchQuery(query);
        int sanitizedLimit = sanitizeLimit(limit);
        String rootUri = buildSkillRootUri(identity);
        log.info("[OpenVikingSkillService] 检索 Skill: query={}, limit={}", normalizedQuery, sanitizedLimit);
        return openVikingClient.find(new OpenVikingFindRequest(
                normalizedQuery,
                rootUri,
                sanitizedLimit,
                sanitizedLimit,
                null,
                true
        ), identity);
    }

    public OpenVikingReadResponse readSkillMain(String name) {
        return readSkillMain(name, currentIdentity());
    }

    public OpenVikingReadResponse readSkillMain(String name, OpenVikingIdentity identity) {
        String skillName = validateSkillName(name);
        String uri = buildSkillDirectoryUri(skillName, identity) + "SKILL.md";
        log.info("[OpenVikingSkillService] 读取 Skill 主文件: name={}, uri={}", skillName, uri);
        return openVikingClient.read(uri, identity);
    }

    /**
     * 读取 Skill L0 摘要（.abstract.md）
     */
    public OpenVikingReadResponse readSkillAbstract(String name) {
        return readSkillAbstract(name, currentIdentity());
    }

    public OpenVikingReadResponse readSkillAbstract(String name, OpenVikingIdentity identity) {
        String skillName = validateSkillName(name);
        String uri = buildSkillDirectoryUri(skillName, identity);
        log.info("[OpenVikingSkillService] 读取 Skill 摘要: name={}, uri={}", skillName, uri);
        return openVikingClient.readAbstract(uri, identity);
    }

    /**
     * 读取 Skill L1 概览（.overview.md）
     */
    public OpenVikingReadResponse readSkillOverview(String name) {
        return readSkillOverview(name, currentIdentity());
    }

    public OpenVikingReadResponse readSkillOverview(String name, OpenVikingIdentity identity) {
        String skillName = validateSkillName(name);
        String uri = buildSkillDirectoryUri(skillName, identity);
        log.info("[OpenVikingSkillService] 读取 Skill 概览: name={}, uri={}", skillName, uri);
        return openVikingClient.readOverview(uri, identity);
    }

    public OpenVikingReadResponse listSkillFiles(String name) {
        return listSkillFiles(name, currentIdentity());
    }

    public OpenVikingReadResponse listSkillFiles(String name, OpenVikingIdentity identity) {
        String skillName = validateSkillName(name);
        String uri = buildSkillDirectoryUri(skillName, identity);
        log.info("[OpenVikingSkillService] 读取 Skill 文件树: name={}, uri={}", skillName, uri);
        return openVikingClient.tree(uri, null, identity);
    }

    public OpenVikingReadResponse readSkillFile(String name, String path) {
        return readSkillFile(name, path, currentIdentity());
    }

    public OpenVikingReadResponse readSkillFile(String name, String path, OpenVikingIdentity identity) {
        String skillName = validateSkillName(name);
        String relativePath = validateSkillRelativePath(path);
        String uri = buildSkillDirectoryUri(skillName, identity) + relativePath;
        log.info("[OpenVikingSkillService] 读取 Skill 文件: name={}, path={}, uri={}", skillName, relativePath, uri);
        return openVikingClient.read(uri, identity);
    }

    public String buildSkillDirectoryUri(String name) {
        return buildSkillDirectoryUri(name, currentIdentity());
    }

    public String buildSkillDirectoryUri(String name, OpenVikingIdentity identity) {
        return buildSkillRootUri(identity) + validateSkillName(name) + "/";
    }

    private OpenVikingIdentity currentIdentity() {
        return ToolRuntimeContext.getOpenVikingIdentity();
    }

    private void validateSkillData(Object data) {
        if (data == null) {
            throw new IllegalArgumentException("OpenViking skill add failed: data is required.");
        }
        if (data instanceof String text) {
            if (text.isBlank()) {
                throw new IllegalArgumentException("OpenViking skill add failed: data is blank.");
            }
            if (looksLikeLocalPath(text)) {
                throw new IllegalArgumentException("OpenViking skill add failed: server local paths are not supported. Use multipart upload instead.");
            }
        }
    }

    private boolean looksLikeLocalPath(String text) {
        String trimmed = text.trim();
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("file:")
                || lower.matches("^[a-z]:[\\\\/].*")
                || lower.startsWith("/")
                || lower.startsWith("~/")
                || lower.startsWith("./")
                || lower.startsWith("../");
    }

    private byte[] normalizeZipIfNeeded(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        byte[] content = file.getBytes();
        if (filename == null || !isZipLikeFilename(filename)) {
            return content;
        }

        ZipShape shape = inspectZip(content);
        if (shape.hasRootSkill()) {
            return content;
        }
        if (shape.singleTopLevelDirectory() == null || !shape.hasSkillUnderSingleTopLevelDirectory()) {
            return content;
        }

        log.info("[OpenVikingSkillService] 规范化 Skill zip: filename={}, stripRoot={}", filename, shape.singleTopLevelDirectory());
        return stripSingleTopLevelDirectory(content, shape.singleTopLevelDirectory());
    }

    private ZipShape inspectZip(byte[] content) throws IOException {
        boolean hasRootSkill = false;
        boolean hasSkillUnderSingleTopLevelDirectory = false;
        Set<String> topLevelNames = new HashSet<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = normalizeZipEntryName(entry.getName());
                if (name.isBlank()) {
                    continue;
                }
                int slashIndex = name.indexOf('/');
                String topLevelName = slashIndex >= 0 ? name.substring(0, slashIndex) : name;
                topLevelNames.add(topLevelName);
                if ("SKILL.md".equals(name)) {
                    hasRootSkill = true;
                }
            }
        }

        String singleTopLevelDirectory = topLevelNames.size() == 1 ? topLevelNames.iterator().next() : null;
        if (singleTopLevelDirectory != null) {
            try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    String name = normalizeZipEntryName(entry.getName());
                    if ((singleTopLevelDirectory + "/SKILL.md").equals(name)) {
                        hasSkillUnderSingleTopLevelDirectory = true;
                        break;
                    }
                }
            }
        }

        return new ZipShape(hasRootSkill, singleTopLevelDirectory, hasSkillUnderSingleTopLevelDirectory);
    }

    private byte[] stripSingleTopLevelDirectory(byte[] content, String topLevelDirectory) throws IOException {
        String prefix = topLevelDirectory + "/";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content));
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = normalizeZipEntryName(entry.getName());
                if (!name.startsWith(prefix) || name.length() == prefix.length()) {
                    continue;
                }
                String strippedName = name.substring(prefix.length());
                ZipEntry strippedEntry = new ZipEntry(strippedName);
                zipOutputStream.putNextEntry(strippedEntry);
                if (!entry.isDirectory()) {
                    zipInputStream.transferTo(zipOutputStream);
                }
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private String normalizeZipEntryName(String name) {
        return name.replace('\\', '/');
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("OpenViking skill upload failed: file is empty.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("OpenViking skill upload failed: filename is blank.");
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".zip") || lowerName.endsWith(".skill"))) {
            throw new IllegalArgumentException("OpenViking skill upload failed: only .zip or .skill files are supported.");
        }
    }

    private boolean isZipLikeFilename(String filename) {
        String lowerName = filename.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".zip") || lowerName.endsWith(".skill");
    }

    private SkillCatalogItem buildSkillCatalogItem(
            String id,
            String fallbackName,
            String path,
            String updatedAt,
            OpenVikingIdentity identity) {
        String abstractText = "";
        try {
            OpenVikingReadResponse abstractResponse = readSkillAbstract(id, identity);
            abstractText = extractSkillAbstractText(abstractResponse);
        } catch (Exception e) {
            log.warn("[OpenVikingSkillService] 读取 Skill 摘要失败，回退为空字符串: id={}, error={}", id, e.getMessage());
        }
        Map<String, String> abstractFields = parseFrontmatterLikeFields(abstractText);
        String name = firstNonBlank(
                abstractFields.get("name"),
                fallbackName,
                id
        );
        String description = firstNonBlank(
                abstractFields.get("description"),
                normalizeAbstractText(abstractText),
                ""
        );
        return new SkillCatalogItem(
                id,
                name,
                path,
                description,
                updatedAt
        );
    }

    private List<SkillCatalogSeed> extractSkillCatalogSeeds(OpenVikingReadResponse response, OpenVikingIdentity identity) {
        if (response == null || response.result() == null) {
            return List.of();
        }
        Object result = response.result();
        if (result instanceof List<?> list) {
            return list.stream()
                    .map(item -> toSkillCatalogSeed(item, identity))
                    .filter(Objects::nonNull)
                    .toList();
        }
        if (result instanceof Map<?, ?> map) {
            Object items = map.get("items");
            if (items instanceof List<?> itemList) {
                return itemList.stream()
                        .map(item -> toSkillCatalogSeed(item, identity))
                        .filter(Objects::nonNull)
                        .toList();
            }
        }
        return List.of();
    }

    private SkillCatalogSeed toSkillCatalogSeed(Object raw, OpenVikingIdentity identity) {
        if (raw instanceof String uri) {
            return createSeedFromUri(uri, null);
        }
        if (raw instanceof Map<?, ?> map) {
            String uri = asText(firstNonNull(map, "uri", "path"));
            if (uri == null || uri.isBlank()) {
                String name = asText(firstNonNull(map, "name", "id"));
                if (name != null && !name.isBlank()) {
                    uri = buildSkillDirectoryUri(name, identity);
                }
            }
            if (uri == null || uri.isBlank()) {
                return null;
            }
            String updatedAt = normalizeUpdatedAt(asText(firstNonNull(map, "updated_at", "updatedAt", "mtime", "modified")));
            return createSeedFromUri(uri, updatedAt);
        }
        return null;
    }

    private SkillCatalogSeed createSeedFromUri(String uri, String updatedAt) {
        String normalizedUri = normalizeSkillUri(uri);
        if (normalizedUri == null) {
            return null;
        }
        String skillId = extractSkillIdFromUri(normalizedUri);
        if (skillId == null) {
            return null;
        }
        return new SkillCatalogSeed(skillId, skillId, normalizedUri, updatedAt);
    }

    private String normalizeSkillUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String trimmed = uri.trim();
        if (trimmed.endsWith("/SKILL.md")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/SKILL.md".length()) + "/";
        }
        if (!isSkillUri(trimmed)) {
            return null;
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private String extractSkillIdFromUri(String uri) {
        int rootEndIndex = findSkillRootEndIndex(uri);
        if (rootEndIndex < 0 || rootEndIndex >= uri.length()) {
            return null;
        }
        String suffix = uri.substring(rootEndIndex);
        int slashIndex = suffix.indexOf('/');
        String candidate = slashIndex >= 0 ? suffix.substring(0, slashIndex) : suffix;
        if (candidate.isBlank()) {
            return null;
        }
        return validateSkillName(candidate);
    }

    private String buildSkillRootUri(OpenVikingIdentity identity) {
        if (identity != null && hasText(identity.agent())) {
            return AGENT_URI_PREFIX + identity.agent().trim() + SKILLS_SEGMENT;
        }
        return LEGACY_SKILL_ROOT_URI;
    }

    private boolean isSkillUri(String uri) {
        return uri.startsWith(LEGACY_SKILL_ROOT_URI) || findSkillRootEndIndex(uri) >= 0;
    }

    private int findSkillRootEndIndex(String uri) {
        if (uri == null || uri.isBlank()) {
            return -1;
        }
        if (uri.startsWith(LEGACY_SKILL_ROOT_URI)) {
            return LEGACY_SKILL_ROOT_URI.length();
        }
        if (!uri.startsWith(AGENT_URI_PREFIX)) {
            return -1;
        }
        int skillsSegmentIndex = uri.indexOf(SKILLS_SEGMENT, AGENT_URI_PREFIX.length());
        if (skillsSegmentIndex < 0) {
            return -1;
        }
        return skillsSegmentIndex + SKILLS_SEGMENT.length();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String extractSkillAbstractText(OpenVikingReadResponse response) {
        if (response == null || response.result() == null) {
            return "";
        }
        Object result = response.result();
        if (result instanceof String text) {
            return text;
        }
        if (result instanceof Map<?, ?> map) {
            return firstNonBlank(
                    asText(map.get("content")),
                    asText(map.get("abstract")),
                    asText(map.get("text")),
                    ""
            );
        }
        return String.valueOf(result);
    }

    private Map<String, String> parseFrontmatterLikeFields(String abstractText) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (abstractText == null || abstractText.isBlank()) {
            return fields;
        }
        String[] lines = abstractText.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith("-")) {
                continue;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }
            String key = line.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT).replace('-', '_');
            String value = line.substring(colonIndex + 1).trim();
            if (!value.isBlank() && !fields.containsKey(key)) {
                fields.put(key, value);
            }
        }
        return fields;
    }

    private String normalizeAbstractText(String abstractText) {
        if (abstractText == null || abstractText.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = abstractText.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()
                    || line.equals("---")
                    || line.startsWith("name:")
                    || line.startsWith("description:")
                    || line.startsWith("tags:")
                    || line.startsWith("allowed_tools:")
                    || line.startsWith("allowed-tools:")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private Object firstNonNull(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private String normalizeUpdatedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toString();
        } catch (DateTimeParseException ignore) {
            return value.trim();
        }
    }

    private String asText(Object value) {
        return value instanceof String text ? text : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String validateSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("OpenViking skill search failed: query is blank.");
        }
        return query.trim();
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null) {
            return 5;
        }
        return Math.max(1, Math.min(limit, 20));
    }

    private String validateSkillRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("OpenViking skill file read failed: path is blank.");
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")
                || normalized.contains("//")
                || normalized.contains("\u0000")) {
            throw new IllegalArgumentException("OpenViking skill file read failed: path must be a safe relative path.");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("OpenViking skill file read failed: path must be a safe relative path.");
            }
        }
        return normalized;
    }

    private String validateSkillName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("OpenViking skill operation failed: skill name is blank.");
        }
        String trimmed = name.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..") || !SAFE_SKILL_NAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("OpenViking skill operation failed: skill name must be an exact safe name.");
        }
        return trimmed;
    }

    private record ZipShape(
            boolean hasRootSkill,
            String singleTopLevelDirectory,
            boolean hasSkillUnderSingleTopLevelDirectory
    ) {
    }

    public record SkillCatalogItem(
            String id,
            String name,
            String path,
            String abstractText,
            String updatedAt
    ) {
    }

    private record SkillCatalogSeed(
            String id,
            String name,
            String path,
            String updatedAt
    ) {
    }
}
