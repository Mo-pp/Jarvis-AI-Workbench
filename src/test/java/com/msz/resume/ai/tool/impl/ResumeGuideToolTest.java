package com.msz.resume.ai.resume.tooling;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeGuideToolTest {

    @Test
    @DisplayName("getResumeGuide 描述应强调工作台优先而非先输出长篇正文")
    void resumeGuideDescriptionShouldPreferWorkbenchArtifact() {
        ToolSpecification spec = ToolSpecifications.toolSpecificationsFrom(new ResumeGuideTool()).stream()
                .filter(tool -> "getResumeGuide".equals(tool.name()))
                .findFirst()
                .orElseThrow();

        String description = spec.description();
        assertTrue(description.contains("放到工作台"));
        assertTrue(description.contains("尽快调用 publishArtifact"));
        assertTrue(description.contains("不要先输出大段纯文本简历草稿"));
    }

    @Test
    @DisplayName("getResumeGuide 返回说明应明确默认先发布 resume artifact")
    void resumeGuideInstructionShouldPreferArtifactFirst() {
        String guide = new ResumeGuideTool().getResumeGuide();

        assertTrue(guide.contains("默认流程应是：整理信息 → 生成结构化 resume → 立即调用 publishArtifact"));
        assertTrue(guide.contains("不要先输出整份长篇简历草稿"));
        assertTrue(guide.contains("发布到工作台后"));
    }
}
