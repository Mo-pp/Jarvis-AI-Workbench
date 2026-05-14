package com.msz.resume.ai.integrations.openviking.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingFindResponse;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingReadResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenVikingSkillToolTest {

    private static OpenVikingProperties createStubProperties() {
        OpenVikingProperties properties = new OpenVikingProperties();
        properties.setBaseUrl("http://localhost:8080");
        properties.setApiKey("test-key");
        properties.setAccount("test-account");
        properties.setUser("test-user");
        properties.setAgent("test-agent");
        properties.setTimeout(Duration.ofSeconds(5));
        return properties;
    }

    @Test
    @DisplayName("skill_search 应限定检索 Skill 根目录并格式化 skill name")
    void shouldSearchSkillRootAndFormatSkillName() {
        OpenVikingFindResponse.MatchedContext context = new OpenVikingFindResponse.MatchedContext(
                "viking://agent/skills/demo-skill/SKILL.md",
                "skill",
                true,
                "Demo skill instructions.",
                "skills",
                0.9123,
                "matched demo skill"
        );
        OpenVikingFindResponse response = new OpenVikingFindResponse(
                "ok",
                new OpenVikingFindResponse.Result(List.of(), List.of(), List.of(context), 1),
                null,
                0.1
        );
        StubOpenVikingClient client = new StubOpenVikingClient();
        client.findResponse = response;
        OpenVikingSkillTool tool = new OpenVikingSkillTool(new OpenVikingSkillService(client));

        String result = tool.openviking_skill_search(" demo ", 99);

        assertEquals("demo", client.lastFindRequest.query());
        assertEquals("viking://agent/skills/", client.lastFindRequest.targetUri());
        assertEquals(20, client.lastFindRequest.limit());
        assertEquals(20, client.lastFindRequest.nodeLimit());
        assertEquals(true, client.lastFindRequest.includeProvenance());
        assertTrue(result.contains("OpenViking Skill search results for: demo"));
        assertTrue(result.contains("scope=viking://agent/skills/"));
        assertTrue(result.contains("Skill name: demo-skill"));
        assertTrue(result.contains("Reason: matched demo skill"));
    }

    @Test
    @DisplayName("skill_read 应读取指定 Skill 的完整 SKILL.md")
    void shouldReadSkillMainFile() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        client.readResponse = new OpenVikingReadResponse("ok", "# Demo\nUse this skill.", null, 0.1);
        OpenVikingSkillTool tool = new OpenVikingSkillTool(new OpenVikingSkillService(client));

        String result = tool.openviking_skill_read("demo-skill");

        assertEquals("viking://agent/skills/demo-skill/SKILL.md", client.lastReadUri);
        assertTrue(result.contains("OpenViking Skill read result"));
        assertTrue(result.contains("name=demo-skill"));
        assertTrue(result.contains("path=SKILL.md"));
        assertTrue(result.contains("# Demo"));
    }

    @Test
    @DisplayName("skill_files 应读取指定 Skill 文件树")
    void shouldReadSkillFilesTree() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        client.treeResponse = new OpenVikingReadResponse("ok", Map.of("items", List.of("SKILL.md", "README.md")), null, 0.1);
        OpenVikingSkillTool tool = new OpenVikingSkillTool(new OpenVikingSkillService(client));

        String result = tool.openviking_skill_files("demo-skill");

        assertEquals("viking://agent/skills/demo-skill/", client.lastTreeUri);
        assertEquals(null, client.lastTreeNodeLimit);
        assertTrue(result.contains("OpenViking Skill files result"));
        assertTrue(result.contains("SKILL.md"));
        assertTrue(result.contains("README.md"));
    }

    @Test
    @DisplayName("skill_read_file 应读取 Skill 内安全相对路径")
    void shouldReadSkillRelativeFile() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        client.readResponse = new OpenVikingReadResponse("ok", "supporting notes", null, 0.1);
        OpenVikingSkillTool tool = new OpenVikingSkillTool(new OpenVikingSkillService(client));

        String result = tool.openviking_skill_read_file("demo-skill", "references/notes.md");

        assertEquals("viking://agent/skills/demo-skill/references/notes.md", client.lastReadUri);
        assertTrue(result.contains("OpenViking Skill read_file result"));
        assertTrue(result.contains("path=references/notes.md"));
        assertTrue(result.contains("supporting notes"));
    }

    @Test
    @DisplayName("skill 工具应返回明确参数校验错误")
    void shouldValidateArguments() {
        OpenVikingSkillTool tool = new OpenVikingSkillTool(new OpenVikingSkillService(new StubOpenVikingClient()));

        assertEquals("OpenViking Skill search failed: query is empty.", tool.openviking_skill_search(" ", null));
        assertEquals("OpenViking Skill search failed: limit must be a positive integer.", tool.openviking_skill_search("demo", 0));
        assertEquals("OpenViking Skill read failed: name is empty.", tool.openviking_skill_read(" "));
        assertEquals("OpenViking Skill files failed: name is empty.", tool.openviking_skill_files(" "));
        assertEquals("OpenViking Skill file read failed: name is empty.", tool.openviking_skill_read_file(" ", "README.md"));
        assertEquals("OpenViking Skill file read failed: path is empty.", tool.openviking_skill_read_file("demo", " "));
        assertEquals("OpenViking skill file read failed: path must be a safe relative path.", tool.openviking_skill_read_file("demo", "../secret.md"));
    }

    @Test
    @DisplayName("客户端异常时应返回友好错误")
    void shouldReturnFriendlyClientErrors() {
        OpenVikingSkillTool tool = new OpenVikingSkillTool(new OpenVikingSkillService(new ErrorStubOpenVikingClient(
                "OpenViking find failed: current key requires tenant headers."
        )));

        String result = tool.openviking_skill_search("demo", null);

        assertEquals("OpenViking find failed: current key requires tenant headers.", result);
    }

    private static final class StubOpenVikingClient extends OpenVikingClient {
        private OpenVikingFindResponse findResponse;
        private OpenVikingReadResponse readResponse;
        private OpenVikingReadResponse treeResponse;
        private OpenVikingFindRequest lastFindRequest;
        private String lastReadUri;
        private String lastTreeUri;
        private Integer lastTreeNodeLimit;

        private StubOpenVikingClient() {
            super(createStubProperties(), new ObjectMapper());
        }

        @Override
        public OpenVikingFindResponse find(OpenVikingFindRequest request) {
            this.lastFindRequest = request;
            return findResponse;
        }

        @Override
        public OpenVikingReadResponse read(String uri) {
            this.lastReadUri = uri;
            return readResponse;
        }

        @Override
        public OpenVikingReadResponse tree(String uri, Integer nodeLimit) {
            this.lastTreeUri = uri;
            this.lastTreeNodeLimit = nodeLimit;
            return treeResponse;
        }
    }

    private static final class ErrorStubOpenVikingClient extends OpenVikingClient {
        private final String message;

        private ErrorStubOpenVikingClient(String message) {
            super(createStubProperties(), new ObjectMapper());
            this.message = message;
        }

        @Override
        public OpenVikingFindResponse find(OpenVikingFindRequest request) {
            throw new OpenVikingClientException(message);
        }
    }
}
