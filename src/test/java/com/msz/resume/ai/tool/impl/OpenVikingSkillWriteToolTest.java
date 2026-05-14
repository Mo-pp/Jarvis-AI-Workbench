package com.msz.resume.ai.integrations.openviking.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.integrations.openviking.core.client.OpenVikingClient;
import com.msz.resume.ai.integrations.openviking.core.config.OpenVikingProperties;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddRequest;
import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSkillAddResponse;
import com.msz.resume.ai.integrations.openviking.core.exception.OpenVikingClientException;
import com.msz.resume.ai.integrations.openviking.core.service.OpenVikingSkillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenVikingSkillWriteToolTest {

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
    @DisplayName("skill_add 应支持原始 SKILL.md 文本")
    void shouldAddRawSkillMarkdown() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        client.addResponse = new OpenVikingSkillAddResponse("ok", Map.of("name", "demo-skill"), null, 0.1);
        OpenVikingSkillWriteTool tool = new OpenVikingSkillWriteTool(new OpenVikingSkillService(client));

        String result = tool.openviking_skill_add("# Demo\nUse this skill.");

        assertEquals("# Demo\nUse this skill.", client.lastAddRequest.data());
        assertEquals(null, client.lastAddRequest.tempFileId());
        assertEquals(false, client.lastAddRequest.waitForProcessing());
        assertTrue(result.contains("OpenViking Skill add result"));
        assertTrue(result.contains("status=ok"));
        assertTrue(result.contains("demo-skill"));
    }

    @Test
    @DisplayName("skill_add 应支持结构化 JSON Skill 数据")
    void shouldAddStructuredSkillJson() {
        StubOpenVikingClient client = new StubOpenVikingClient();
        client.addResponse = new OpenVikingSkillAddResponse("ok", Map.of("name", "json-skill"), null, 0.1);
        OpenVikingSkillWriteTool tool = new OpenVikingSkillWriteTool(new OpenVikingSkillService(client));

        String result = tool.openviking_skill_add("{\"name\":\"json-skill\",\"content\":\"# Demo\"}");

        assertInstanceOf(Map.class, client.lastAddRequest.data());
        assertEquals("json-skill", ((Map<?, ?>) client.lastAddRequest.data()).get("name"));
        assertTrue(result.contains("json-skill"));
    }

    @Test
    @DisplayName("skill_add 应拒绝空内容和引用性内容")
    void shouldRejectBlankAndReferenceData() {
        OpenVikingSkillWriteTool tool = new OpenVikingSkillWriteTool(new OpenVikingSkillService(new StubOpenVikingClient()));

        assertEquals("OpenViking Skill add failed: data is empty.", tool.openviking_skill_add("   "));
        assertEquals(
                "OpenViking Skill add failed: data must contain the full Skill content, not a reference to previous content.",
                tool.openviking_skill_add("刚刚那份")
        );
    }

    @Test
    @DisplayName("skill_add 应拒绝路径、URL 和 zip 引用")
    void shouldRejectPathUrlAndZipReferences() {
        OpenVikingSkillWriteTool tool = new OpenVikingSkillWriteTool(new OpenVikingSkillService(new StubOpenVikingClient()));

        assertEquals(
                "OpenViking Skill add failed: data must contain inline Skill content, not a path, URL, zip, or upload reference.",
                tool.openviking_skill_add("https://example.com/demo/SKILL.md")
        );
        assertEquals(
                "OpenViking skill add failed: server local paths are not supported. Use multipart upload instead.",
                tool.openviking_skill_add("C:\\skills\\demo\\SKILL.md")
        );
        assertEquals(
                "OpenViking Skill add failed: data must contain inline Skill content, not a path, URL, zip, or upload reference.",
                tool.openviking_skill_add("demo.zip")
        );
    }

    @Test
    @DisplayName("skill_add 客户端异常时应返回友好错误")
    void shouldReturnFriendlyClientErrors() {
        OpenVikingSkillWriteTool tool = new OpenVikingSkillWriteTool(new OpenVikingSkillService(new ErrorStubOpenVikingClient(
                "OpenViking add skill request was rejected: HTTP 409. Response body: conflict"
        )));

        String result = tool.openviking_skill_add("# Demo\nUse this skill.");

        assertEquals("OpenViking add skill request was rejected: HTTP 409. Response body: conflict", result);
    }

    private static final class StubOpenVikingClient extends OpenVikingClient {
        private OpenVikingSkillAddResponse addResponse;
        private OpenVikingSkillAddRequest lastAddRequest;

        private StubOpenVikingClient() {
            super(createStubProperties(), new ObjectMapper());
        }

        @Override
        public OpenVikingSkillAddResponse addSkill(OpenVikingSkillAddRequest request) {
            this.lastAddRequest = request;
            return addResponse;
        }
    }

    private static final class ErrorStubOpenVikingClient extends OpenVikingClient {
        private final String message;

        private ErrorStubOpenVikingClient(String message) {
            super(createStubProperties(), new ObjectMapper());
            this.message = message;
        }

        @Override
        public OpenVikingSkillAddResponse addSkill(OpenVikingSkillAddRequest request) {
            throw new OpenVikingClientException(message);
        }
    }
}
