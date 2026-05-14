package com.msz.resume.ai.integrations.openviking.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingWriteResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenVikingUserMemoryService 单元测试。
 */
class OpenVikingUserMemoryServiceTest {

    @Test
    @DisplayName("canonicalizeMemoryKey 应规范化大小写、空格和非法字符")
    void shouldCanonicalizeMemoryKey() {
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(new StubOpenVikingClient());

        assertEquals("favorite_adult_actresses", service.canonicalizeMemoryKey(" Favorite Adult Actresses "));
        assertEquals("language-and_workflow", service.canonicalizeMemoryKey("Language-and Workflow"));
        assertThrows(IllegalArgumentException.class, () -> service.canonicalizeMemoryKey("中文"));
    }

    @Test
    @DisplayName("buildMemoryFilename 应按 type 和 key 生成稳定文件名")
    void shouldBuildStableMemoryFilename() {
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(new StubOpenVikingClient());

        assertEquals("user_favorite_adult_actresses.md", service.buildMemoryFilename("user", "favorite adult actresses"));
        assertEquals("feedback_language_and_workflow.md", service.buildMemoryFilename("feedback", "language_and_workflow"));
        assertEquals("project_current_release.md", service.buildMemoryFilename("project", "current release"));
        assertEquals("reference_grafana_latency.md", service.buildMemoryFilename("reference", "grafana latency"));
    }

    @Test
    @DisplayName("isValidMemoryFilename 应接受新格式和 legacy feedback hash")
    void shouldValidateNewAndLegacyFilenames() {
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(new StubOpenVikingClient());

        assertTrue(service.isValidMemoryFilename("user_favorite_adult_actresses.md"));
        assertTrue(service.isValidMemoryFilename("feedback_language_and_workflow.md"));
        assertTrue(service.isValidMemoryFilename("feedback_667720200151965d.md"));
    }

    @Test
    @DisplayName("buildMemoryMarkdown 应包含 type、key 和 schema_version")
    void shouldBuildMemoryMarkdownWithFrontmatter() {
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(new StubOpenVikingClient());

        String markdown = service.buildMemoryMarkdown("user", "favorite adult actresses", "喜欢的av女优", "用户喜欢的av女优列表", "桥本杉菜");

        assertTrue(markdown.contains("name: '喜欢的av女优'"));
        assertTrue(markdown.contains("description: '用户喜欢的av女优列表'"));
        assertTrue(markdown.contains("type: user"));
        assertTrue(markdown.contains("key: favorite_adult_actresses"));
        assertTrue(markdown.contains("schema_version: 1"));
        assertTrue(markdown.endsWith("桥本杉菜\n"));
    }

    @Test
    @DisplayName("parseMemoryIndex 应解析新格式和 legacy 格式")
    void shouldParseNewAndLegacyMemoryIndex() {
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(new StubOpenVikingClient());
        String index = """
                # User Memory Index

                ## User
                - [喜欢的av女优](user_favorite_adult_actresses.md) — 用户喜欢的av女优列表

                ## Feedback
                - [旧偏好](feedback_667720200151965d.md) — 旧 feedback 记忆
                """;

        List<OpenVikingUserMemoryService.MemoryIndexEntry> entries = service.parseMemoryIndex(index);

        assertEquals(2, entries.size());
        assertEquals("user", entries.get(0).type());
        assertEquals("user_favorite_adult_actresses.md", entries.get(0).filename());
        assertEquals("feedback", entries.get(1).type());
        assertEquals("feedback_667720200151965d.md", entries.get(1).filename());
    }

    @Test
    @DisplayName("appendOrReplaceIndexLine 应按 filename 替换而不是新增重复项")
    void shouldReplaceIndexLineByFilename() {
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(new StubOpenVikingClient());
        String index = """
                # User Memory Index

                ## User
                - [喜欢的av女优](user_favorite_adult_actresses.md) — 旧描述
                """;
        OpenVikingUserMemoryService.MemoryIndexEntry newEntry = new OpenVikingUserMemoryService.MemoryIndexEntry(
                "喜欢的av女优",
                "user_favorite_adult_actresses.md",
                "新描述",
                "user"
        );

        String updated = service.appendOrReplaceIndexLine(index, newEntry);

        assertTrue(updated.contains("- [喜欢的av女优](user_favorite_adult_actresses.md) — 新描述"));
        assertEquals(1, countOccurrences(updated, "user_favorite_adult_actresses.md"));
    }

    @Test
    @DisplayName("saveMemory 同 type/key 二次保存应写同一个文件")
    void shouldSaveSameTypeAndKeyToSameFile() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(client);

        OpenVikingUserMemoryService.SaveMemoryResult first = service.saveMemory(
                "u-demo-001", "user", "favorite adult actresses", "喜欢的av女优", "旧描述", "桥本杉菜");
        OpenVikingUserMemoryService.SaveMemoryResult second = service.saveMemory(
                "u-demo-001", "user", "favorite adult actresses", "喜欢的av女优", "新描述", "桥本杉菜、千早爱音");

        assertEquals("user_favorite_adult_actresses.md", first.filename());
        assertEquals(first.filename(), second.filename());
        assertTrue(client.files.containsKey("viking://user/u-demo-001/memories/user_favorite_adult_actresses.md"));
        assertFalse(client.files.containsKey("viking://user/u-demo-001/memories/MEMORY.md"));
    }

    @Test
    @DisplayName("saveMemory 不应直接写 OpenViking 派生语义文件")
    void shouldNotWriteDerivedSemanticFiles() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(client);

        service.saveMemory("u-demo-001", "user", "favorite food", "喜欢的食物", "用户喜欢的食物", "牛肉面");

        assertFalse(client.writeRequests.stream().anyMatch(request -> request.uri().endsWith("/.abstract.md")));
        assertFalse(client.writeRequests.stream().anyMatch(request -> request.uri().endsWith("/.overview.md")));
    }

    @Test
    @DisplayName("saveMemory 只写 L2，不维护 MEMORY.md")
    void shouldOnlyWriteL2MemoryFile() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(client);

        OpenVikingUserMemoryService.SaveMemoryResult result = service.saveMemory(
                "u-demo-001", "user", "favorite food", "喜欢的食物", "用户喜欢的食物", "牛肉面");

        assertEquals("ok", result.status());
        assertTrue(result.memorySaved());
        assertTrue(result.readStrategy().contains(".abstract.md"));
        assertTrue(client.files.containsKey("viking://user/u-demo-001/memories/user_favorite_food.md"));
        assertFalse(client.files.containsKey("viking://user/u-demo-001/memories/MEMORY.md"));
        assertFalse(client.writeRequests.stream().anyMatch(request -> request.uri().endsWith("/MEMORY.md")));
    }

    @Test
    @DisplayName("提示词应说明目录级 overview、文件级 read 和通用 URI scope")
    void shouldGuideModelToUseDirectoryOverviewAndFileRead() {
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(new StubOpenVikingClient());

        String section = service.buildPromptMemoryOverviewSection(List.of(
                new OpenVikingUserMemoryService.MemoryLayerContent("L0 Abstract", ".abstract.md", "summary")
        ));

        assertTrue(section.contains("viking://user/{userId}/memories/`) and level `overview`"));
        assertTrue(section.contains("viking://user/{userId}/memories/{type}_{key}.md` and level `read`"));
        assertTrue(section.contains("viking://user/{userId}/`"));
        assertTrue(section.contains("viking://agent/{agentId}/`"));
        assertTrue(section.contains("viking://session/{sessionId}/`"));
        assertTrue(section.contains("viking://resources/`"));
        assertFalse(section.contains("overview URI (`viking://user/{userId}/memories/.overview.md`)"));
    }

    @Test
    @DisplayName("saveMemory 遇到 busy 但 read-after-write 内容一致时应按成功处理")
    void shouldTreatBusyAsSuccessWhenReadAfterWriteMatches() {
        BusyAfterWriteOpenVikingClient client = new BusyAfterWriteOpenVikingClient();
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(client);

        OpenVikingUserMemoryService.SaveMemoryResult result = service.saveMemory(
                "u-demo-001", "user", "test2", "test文档2", "测试文档", "666Jarvis");

        assertEquals("ok", result.status());
        assertTrue(result.memorySaved());
        assertTrue(client.files.containsKey("viking://user/u-demo-001/memories/user_test2.md"));
    }

    @Test
    @DisplayName("saveMemory 遇到文件已存在且内容一致时不应继续 replace")
    void shouldSkipReplaceWhenExistingContentMatches() {
        ExistingMatchingOpenVikingClient client = new ExistingMatchingOpenVikingClient();
        OpenVikingUserMemoryService service = new OpenVikingUserMemoryService(client);
        String markdown = service.buildMemoryMarkdown("user", "test2", "test文档2", "测试文档", "666Jarvis");
        client.files.put("viking://user/u-demo-001/memories/user_test2.md", markdown);

        OpenVikingUserMemoryService.SaveMemoryResult result = service.saveMemory(
                "u-demo-001", "user", "test2", "test文档2", "测试文档", "666Jarvis");

        assertEquals("ok", result.status());
        assertTrue(result.memorySaved());
        assertFalse(client.writeRequests.stream().anyMatch(request -> "replace".equals(request.mode())));
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static OpenVikingProperties createProperties() {
        OpenVikingProperties properties = new OpenVikingProperties();
        properties.setBaseUrl("http://localhost:1933");
        properties.setApiKey("dummy");
        properties.setTimeout(Duration.ofSeconds(10));
        return properties;
    }

    private static class BusyAfterWriteOpenVikingClient extends StubOpenVikingClient {
        @Override
        public OpenVikingWriteResponse write(OpenVikingWriteRequest request) {
            writeRequests.add(request);
            files.put(request.uri(), request.content());
            throw new OpenVikingClientException("OpenViking write request was rejected: HTTP 400. Response body: {\"status\":\"error\",\"error\":{\"code\":\"INVALID_ARGUMENT\",\"message\":\"resource is busy and cannot be written now: " + request.uri() + "\"}}");
        }
    }

    private static class ExistingMatchingOpenVikingClient extends StubOpenVikingClient {
        @Override
        public OpenVikingWriteResponse write(OpenVikingWriteRequest request) {
            if ("create".equals(request.mode()) && files.containsKey(request.uri())) {
                writeRequests.add(request);
                throw new OpenVikingClientException("409 conflict: file exists");
            }
            return super.write(request);
        }
    }

    private static class StubOpenVikingClient extends OpenVikingClient {
        protected final Map<String, String> files = new java.util.HashMap<>();
        protected final List<OpenVikingWriteRequest> writeRequests = new ArrayList<>();

        private StubOpenVikingClient() {
            super(createProperties(), new ObjectMapper());
        }

        @Override
        public OpenVikingReadResponse read(String uri) {
            String content = files.get(uri);
            if (content == null) {
                throw new OpenVikingClientException("not found");
            }
            return new OpenVikingReadResponse("ok", content, null, 0.1);
        }

        @Override
        public OpenVikingWriteResponse write(OpenVikingWriteRequest request) {
            writeRequests.add(request);
            if ("create".equals(request.mode()) && files.containsKey(request.uri())) {
                throw new OpenVikingClientException("409 conflict: file exists");
            }
            files.put(request.uri(), request.content());
            return new OpenVikingWriteResponse(
                    "ok",
                    new OpenVikingWriteResponse.Result(request.uri(), request.mode(), "ok", null, null),
                    null,
                    0.1
            );
        }
    }
}
