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

    @Test
    @DisplayName("getResumeGuide 返回说明应禁止占位并支持分区排版控制")
    void resumeGuideInstructionShouldAvoidPlaceholdersAndExposeStyleControls() {
        String guide = new ResumeGuideTool().getResumeGuide();

        assertTrue(guide.contains("不要输出 XXX、未命名候选人、目标职位、学校名称、公司名称、项目名称、奖项名称等占位文本"));
        assertTrue(guide.contains("resumeStyle.pageMarginX / pageMarginY"));
        assertTrue(guide.contains("summary、education、work、project、campus、award、skills"));
        assertTrue(guide.contains("生成简历时要主动提高后续 evaluateResume 的质量评分"));
        assertTrue(guide.contains("作为 sourceFileId 传给 evaluateResume"));
        assertTrue(guide.contains("不要把上传文件全文复制到 originalResumeText"));
        assertTrue(guide.contains("原始简历里的项目地址、GitHub、在线演示"));
        assertTrue(guide.contains("出现“技术栈：...”时写入 techStack"));
        assertTrue(guide.contains("默认不要生成个人总结"));
    }

    @Test
    @DisplayName("getResumeGuide 返回说明应包含项目经历业务化硬规则")
    void resumeGuideInstructionShouldContainProjectBusinessRules() {
        String guide = new ResumeGuideTool().getResumeGuide();

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
