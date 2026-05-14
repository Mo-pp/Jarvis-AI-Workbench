package com.msz.resume.ai.integrations.openviking.core.service;

import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingTempUploadResponse;
import com.msz.resume.ai.integrations.openviking.core.model.OpenVikingIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenVikingSkillServiceTest {

    @Test
    @DisplayName("listSkills 应固定读取 Skill 根目录")
    void shouldListSkillRoot() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        OpenVikingReadResponse expected = new OpenVikingReadResponse("ok", Map.of("items", java.util.List.of()), null, null);
        when(client.list("viking://agent/jarvis/skills/", null, identity)).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        OpenVikingReadResponse response = service.listSkills(identity);

        assertEquals(expected, response);
        verify(client).list("viking://agent/jarvis/skills/", null, identity);
    }

    @Test
    @DisplayName("addSkill 应拒绝空 data")
    void shouldRejectBlankData() {
        OpenVikingSkillService service = new OpenVikingSkillService(mock(OpenVikingClient.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addSkill("   ")
        );

        assertEquals("OpenViking skill add failed: data is blank.", exception.getMessage());
    }

    @Test
    @DisplayName("addSkill 应拒绝服务器本地路径")
    void shouldRejectLocalPathData() {
        OpenVikingSkillService service = new OpenVikingSkillService(mock(OpenVikingClient.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addSkill("C:\\skills\\demo\\SKILL.md")
        );

        assertEquals("OpenViking skill add failed: server local paths are not supported. Use multipart upload instead.", exception.getMessage());
    }

    @Test
    @DisplayName("uploadSkill 应先 temp_upload 再 add_skill")
    void shouldUploadThenAddSkill() throws Exception {
        OpenVikingClient client = mock(OpenVikingClient.class);
        when(client.tempUpload(eq("demo.skill"), any(byte[].class), eq("application/octet-stream"), isNull()))
                .thenReturn(new OpenVikingTempUploadResponse("ok", new OpenVikingTempUploadResponse.Result("upload_001.md"), null, null));
        OpenVikingSkillAddResponse expected = new OpenVikingSkillAddResponse("ok", Map.of("name", "demo"), null, null);
        when(client.addSkill(any(OpenVikingSkillAddRequest.class), isNull())).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.skill",
                "application/octet-stream",
                zipBytes(Map.of(
                        "demo-main/SKILL.md", "# Demo",
                        "demo-main/README.md", "readme"
                ))
        );

        OpenVikingSkillAddResponse response = service.uploadSkill(file);

        assertEquals(expected, response);
        verify(client).tempUpload(eq("demo.skill"), any(byte[].class), eq("application/octet-stream"), isNull());
        verify(client).addSkill(new OpenVikingSkillAddRequest(null, "upload_001.md", false, null), null);
    }

    @Test
    @DisplayName("uploadSkill 应兼容 zip 外层单目录")
    void shouldNormalizeZipWithSingleRootDirectory() throws Exception {
        OpenVikingClient client = mock(OpenVikingClient.class);
        when(client.tempUpload(eq("demo.zip"), any(byte[].class), eq("application/zip"), isNull()))
                .thenReturn(new OpenVikingTempUploadResponse("ok", new OpenVikingTempUploadResponse.Result("upload_zip_001"), null, null));
        OpenVikingSkillAddResponse expected = new OpenVikingSkillAddResponse("ok", Map.of("name", "demo"), null, null);
        when(client.addSkill(any(OpenVikingSkillAddRequest.class), isNull())).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.zip",
                "application/zip",
                zipBytes(Map.of(
                        "demo-main/SKILL.md", "# Demo",
                        "demo-main/README.md", "readme"
                ))
        );

        OpenVikingSkillAddResponse response = service.uploadSkill(file);

        assertEquals(expected, response);
        verify(client).tempUpload(eq("demo.zip"), any(byte[].class), eq("application/zip"), isNull());
        verify(client).addSkill(new OpenVikingSkillAddRequest(null, "upload_zip_001", false, null), null);
    }

    @Test
    @DisplayName("uploadSkill 应支持 .skill 扩展名")
    void shouldAcceptSkillArchiveExtension() throws Exception {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        when(client.tempUpload(eq("demo.skill"), any(byte[].class), eq("application/octet-stream"), eq(identity)))
                .thenReturn(new OpenVikingTempUploadResponse("ok", new OpenVikingTempUploadResponse.Result("upload_skill_001"), null, null));
        OpenVikingSkillAddResponse expected = new OpenVikingSkillAddResponse("ok", Map.of("name", "demo"), null, null);
        when(client.addSkill(any(OpenVikingSkillAddRequest.class), eq(identity))).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.skill",
                "application/octet-stream",
                zipBytes(Map.of(
                        "demo-main/SKILL.md", "# Demo",
                        "demo-main/README.md", "readme"
                ))
        );

        OpenVikingSkillAddResponse response = service.uploadSkill(file, identity);

        assertEquals(expected, response);
        verify(client).tempUpload(eq("demo.skill"), any(byte[].class), eq("application/octet-stream"), eq(identity));
        verify(client).addSkill(new OpenVikingSkillAddRequest(null, "upload_skill_001", false, null), identity);
    }

    @Test
    @DisplayName("uploadSkill 只接受 zip 或 .skill")
    void shouldRejectUnsupportedUploadName() {
        OpenVikingSkillService service = new OpenVikingSkillService(mock(OpenVikingClient.class));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "SKILL.md",
                "text/markdown",
                "demo".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadSkill(file)
        );

        assertEquals("OpenViking skill upload failed: only .zip or .skill files are supported.", exception.getMessage());
    }

    @Test
    @DisplayName("searchSkills 应限定在 Skill 根目录检索")
    void shouldSearchSkillRoot() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        OpenVikingFindResponse expected = new OpenVikingFindResponse("ok", null, null, null);
        when(client.find(any(OpenVikingFindRequest.class), eq(identity))).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        OpenVikingFindResponse response = service.searchSkills("  demo  ", 50, identity);

        assertEquals(expected, response);
        verify(client).find(new OpenVikingFindRequest("demo", "viking://agent/jarvis/skills/", 20, 20, null, true), identity);
    }

    @Test
    @DisplayName("readSkillMain 应读取指定 Skill 的 SKILL.md")
    void shouldReadSkillMainFile() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        OpenVikingReadResponse expected = new OpenVikingReadResponse("ok", Map.of("content", "# Demo"), null, null);
        when(client.read("viking://agent/jarvis/skills/demo-skill/SKILL.md", identity)).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        OpenVikingReadResponse response = service.readSkillMain("demo-skill", identity);

        assertEquals(expected, response);
        verify(client).read("viking://agent/jarvis/skills/demo-skill/SKILL.md", identity);
    }

    @Test
    @DisplayName("listSkillFiles 应读取指定 Skill 文件树")
    void shouldListSkillFileTree() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        OpenVikingReadResponse expected = new OpenVikingReadResponse("ok", Map.of("items", java.util.List.of()), null, null);
        when(client.tree("viking://agent/jarvis/skills/demo-skill/", null, identity)).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        OpenVikingReadResponse response = service.listSkillFiles("demo-skill", identity);

        assertEquals(expected, response);
        verify(client).tree("viking://agent/jarvis/skills/demo-skill/", null, identity);
    }

    @Test
    @DisplayName("readSkillFile 应读取 Skill 内安全相对路径")
    void shouldReadSkillRelativeFile() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        OpenVikingReadResponse expected = new OpenVikingReadResponse("ok", Map.of("content", "notes"), null, null);
        when(client.read("viking://agent/jarvis/skills/demo-skill/references/notes.md", identity)).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        OpenVikingReadResponse response = service.readSkillFile("demo-skill", "./references/notes.md", identity);

        assertEquals(expected, response);
        verify(client).read("viking://agent/jarvis/skills/demo-skill/references/notes.md", identity);
    }

    @Test
    @DisplayName("readSkillFile 应拒绝路径穿越")
    void shouldRejectUnsafeSkillRelativePath() {
        OpenVikingSkillService service = new OpenVikingSkillService(mock(OpenVikingClient.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.readSkillFile("demo-skill", "../secret.md")
        );

        assertEquals("OpenViking skill file read failed: path must be a safe relative path.", exception.getMessage());
    }

    @Test
    @DisplayName("deleteSkill 应递归删除精确 Skill 目录")
    void shouldDeleteExactSkillDirectoryRecursively() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        Map<String, Object> result = service.deleteSkill("demo-skill", identity);

        assertEquals("demo-skill", result.get("name"));
        assertEquals("viking://agent/jarvis/skills/demo-skill/", result.get("uri"));
        assertEquals(true, result.get("recursive"));
        verify(client).remove("viking://agent/jarvis/skills/demo-skill/", true, identity);
    }

    @Test
    @DisplayName("listSkills 显式 identity 时应透传给客户端")
    void shouldListSkillsWithExplicitIdentity() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        OpenVikingReadResponse expected = new OpenVikingReadResponse("ok", Map.of("items", java.util.List.of()), null, null);
        when(client.list("viking://agent/jarvis/skills/", null, identity)).thenReturn(expected);
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        OpenVikingReadResponse response = service.listSkills(identity);

        assertEquals(expected, response);
        verify(client).list("viking://agent/jarvis/skills/", null, identity);
        verify(client, never()).list("viking://agent/skills/", null);
    }

    @Test
    @DisplayName("listSkillCatalog 应解析 name description path 和 updatedAt")
    void shouldBuildSkillCatalogItems() {
        OpenVikingClient client = mock(OpenVikingClient.class);
        OpenVikingIdentity identity = new OpenVikingIdentity("acc-1", "user-1", "jarvis");
        when(client.list("viking://agent/jarvis/skills/", null, identity)).thenReturn(
                new OpenVikingReadResponse("ok", Map.of(
                        "items", java.util.List.of(
                                Map.of(
                                        "uri", "viking://agent/jarvis/skills/codemap/",
                                        "updated_at", "2026-05-10T12:00:00Z"
                                )
                        )
                ), null, null)
        );
        when(client.readAbstract("viking://agent/jarvis/skills/codemap/", identity)).thenReturn(
                new OpenVikingReadResponse("ok", """
                        ---
                        name: Code Review Assistant
                        description: 用于代码审查场景，聚焦风险、回归点、测试缺口和可维护性问题。
                        tags:
                          - review
                        """, null, null)
        );
        OpenVikingSkillService service = new OpenVikingSkillService(client);

        java.util.List<OpenVikingSkillService.SkillCatalogItem> items = service.listSkillCatalog(identity);

        assertEquals(1, items.size());
        assertEquals("codemap", items.getFirst().id());
        assertEquals("Code Review Assistant", items.getFirst().name());
        assertEquals("viking://agent/jarvis/skills/codemap/", items.getFirst().path());
        assertEquals("用于代码审查场景，聚焦风险、回归点、测试缺口和可维护性问题。", items.getFirst().abstractText());
        assertEquals("2026-05-10T12:00Z", items.getFirst().updatedAt());
    }

    @Test
    @DisplayName("deleteSkill 应拒绝路径穿越或模糊路径")
    void shouldRejectUnsafeSkillName() {
        OpenVikingSkillService service = new OpenVikingSkillService(mock(OpenVikingClient.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteSkill("../demo")
        );

        assertEquals("OpenViking skill operation failed: skill name must be an exact safe name.", exception.getMessage());
    }

    private byte[] zipBytes(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }
}
