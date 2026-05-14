package com.msz.resume.ai.resume.tooling;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeOptimizeGuideToolTest {

    @Test
    @DisplayName("getOptimizeGuide 描述应强调优化结果优先进入工作台")
    void optimizeGuideDescriptionShouldPreferWorkbenchArtifact() {
        ToolSpecification spec = ToolSpecifications.toolSpecificationsFrom(new ResumeOptimizeGuideTool()).stream()
                .filter(tool -> "getOptimizeGuide".equals(tool.name()))
                .findFirst()
                .orElseThrow();

        String description = spec.description();
        assertTrue(description.contains("输出优化结果到工作台"));
        assertTrue(description.contains("优先产出结构化 optimize_result"));
        assertTrue(description.contains("不要默认先写成长篇分析散文"));
    }

    @Test
    @DisplayName("getOptimizeGuide 返回说明应鼓励附带 optimizedResume")
    void optimizeGuideInstructionShouldEncourageOptimizedResume() {
        String guide = new ResumeOptimizeGuideTool().getOptimizeGuide();

        assertTrue(guide.contains("优先产出 workbench artifact"));
        assertTrue(guide.contains("尽量把它放进 optimizedResume 字段"));
    }
}
