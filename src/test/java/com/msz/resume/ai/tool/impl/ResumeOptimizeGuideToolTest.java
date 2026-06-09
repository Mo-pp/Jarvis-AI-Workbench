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

    @Test
    @DisplayName("getOptimizeGuide 返回说明应包含项目经历业务化硬规则")
    void optimizeGuideInstructionShouldContainProjectBusinessRules() {
        String guide = new ResumeOptimizeGuideTool().getOptimizeGuide();

        assertTrue(guide.contains("项目经历业务化硬规则"));
        assertTrue(guide.contains("每个重点项目的 bullet 至少 50% 必须以业务场景、用户动作、用户流程、交付对象或业务价值开头"));
        assertTrue(guide.contains("README 只能作为弱证据"));
        assertTrue(guide.contains("业务场景/用户动作 + 技术动作 + 结果/X占位指标"));
        assertTrue(guide.contains("受证据约束的业务优化故事构造"));
        assertTrue(guide.contains("缺少现成业务故事时"));
        assertTrue(guide.contains("必须基于真实功能链路主动编造优化故事"));
        assertTrue(guide.contains("提升 X%"));
        assertTrue(guide.contains("下降 Xms"));
        assertTrue(guide.contains("转化率提升 X%"));
        assertTrue(guide.contains("采纳率提升 X%"));
        assertTrue(guide.contains("严禁把未实测的 X 替换成具体数字"));
        assertTrue(guide.contains("不要在项目描述里写“待实测指标/测量方法”"));
        assertTrue(guide.contains("至少形成 3 个可写入简历的 X 占位指标候选"));
    }
}
