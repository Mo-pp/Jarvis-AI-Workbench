package com.msz.resume.ai.chat.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.dto.MatchAnalysis;
import com.msz.resume.ai.resume.dto.OptimizeResult;
import com.msz.resume.ai.resume.dto.ResumeVO;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactToolTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("publishArtifact 返回结构化 optimize_result payload")
    void publishArtifactShouldReturnStructuredOptimizeResult() throws Exception {
        ArtifactTool tool = new ArtifactTool();
        OptimizeResult optimizeResult = OptimizeResult.builder()
                .matchScore(75)
                .matchAnalysis(MatchAnalysis.builder()
                        .matchedSkills(List.of("Java"))
                        .build())
                .optimizedResume(ResumeVO.builder()
                        .basicInfo(ResumeVO.BasicInfo.builder()
                                .name("莫仕铮")
                                .build())
                        .build())
                .build();

        String result = tool.publishArtifact("optimize_result", null, optimizeResult, null, null);

        JsonNode root = OBJECT_MAPPER.readTree(result);
        assertEquals("optimize_result", root.path("type").asText());
        assertEquals(75, root.path("payload").path("matchScore").asInt());
        assertEquals("莫仕铮", root.path("payload").path("optimizedResume").path("basicInfo").path("name").asText());
    }

    @Test
    @DisplayName("publishArtifact 对缺失结构化 payload 返回错误")
    void publishArtifactShouldReturnErrorWhenPayloadMissing() {
        ArtifactTool tool = new ArtifactTool();

        String result = tool.publishArtifact("optimize_result", null, null, null, null);

        assertTrue(result.contains("\"type\":\"error\""));
        assertTrue(result.contains("artifact 内容缺少必要字段"));
    }

    @Test
    @DisplayName("publishArtifact 描述应强调简历场景优先直接发布到工作台")
    void publishArtifactDescriptionShouldPreferWorkbenchForResumeFlows() {
        ToolSpecification spec = ToolSpecifications.toolSpecificationsFrom(new ArtifactTool()).stream()
                .filter(tool -> "publishArtifact".equals(tool.name()))
                .findFirst()
                .orElseThrow();

        assertTrue(spec.description().contains("resume generation or resume optimization requests"));
        assertTrue(spec.description().contains("publish the artifact immediately"));
    }
}
