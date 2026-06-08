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

    @Test
    @DisplayName("getOptimizeGuide 返回说明应禁止占位并支持 resumeStyle")
    void optimizeGuideInstructionShouldAvoidPlaceholdersAndExposeStyleControls() {
        String guide = new ResumeOptimizeGuideTool().getOptimizeGuide();

        assertTrue(guide.contains("不要为了完整度补 XXX、未命名、目标职位、学校名称、公司名称、项目名称等占位文字"));
        assertTrue(guide.contains("optimizedResume 可以包含 resumeStyle"));
        assertTrue(guide.contains("sections.summary/education/work/project/campus/award/skills"));
        assertTrue(guide.contains("提高 evaluateResume 的简历质量评分和 JD 匹配评分"));
        assertTrue(guide.contains("参数 sourceFileId 使用注入文件元信息里的 fileId"));
        assertTrue(guide.contains("不要把上传文件全文复制到 originalResumeText"));
        assertTrue(guide.contains("原始简历里的项目地址、GitHub、在线演示"));
        assertTrue(guide.contains("必须写入 techStack"));
        assertTrue(guide.contains("默认不要新增 summary"));
    }
}
