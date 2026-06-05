package com.msz.resume.ai.resume.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.dto.MatchAnalysis;
import com.msz.resume.ai.resume.dto.ResumeToolResult;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 简历优化指南工具（延迟工具）
 *
 * <p>返回 JD 匹配分析指南和框架，帮助主 LLM 分析简历与目标岗位的匹配度。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>工具只返回静态指南，不调用 LLM</li>
 *   <li>主 LLM 根据指南自己分析匹配度</li>
 *   <li>节省 token，保持主 LLM 控制权</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <p>当用户说"帮我优化简历"、"针对这个JD优化"时，主 LLM 调用此工具获取分析指南。
 */
@Slf4j
@CoreTool
@Component
public class ResumeOptimizeGuideTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 获取简历优化指南
     *
     * <p>返回包含以下内容的指南：
     * <ul>
     *   <li>instruction - 详细的分析步骤和优化原则</li>
     *   <li>analysisFramework - 分析框架（必备技能、加分项等）</li>
     *   <li>outputFormat - 期望的输出格式</li>
     * </ul>
     *
     * @return JSON 格式的优化指南
     */
    @Tool("获取简历优化指南。当用户需要优化简历、按 JD 匹配、输出优化结果到工作台时调用。若当前上下文已包含简历内容和优化所需信息，应优先产出结构化 optimize_result，必要时连同 optimizedResume 一起通过 publishArtifact 发布，不要默认先写成长篇分析散文。")
    public String getOptimizeGuide() {
        log.info("[ResumeOptimizeGuideTool] 返回简历优化指南");

        ResumeToolResult result = ResumeToolResult.ofOptimizeGuide(
                INSTRUCTION,
                createAnalysisFramework(),
                createOutputFormat()
        );

        return toJson(result);
    }

    // ==================== 优化指南内容 ====================

    /**
     * 优化指南说明
     */
    private static final String INSTRUCTION = """
            ## JD 匹配分析指南

            你是一位资深的技术招聘专家，请分析求职者的简历与目标岗位 JD 的匹配程度。

            ### 分析步骤

            #### 1. 提取 JD 关键要求
            从 JD 中提取以下信息：
            - **必备技能**：JD 中明确要求的技能（如"必须掌握"、"要求熟悉"）
            - **加分技能**：JD 中提到"优先"、"加分"、"最好有"的技能
            - **经验要求**：工作年限、行业背景要求
            - **学历要求**：最低学历要求

            #### 2. 匹配度分析
            对比简历内容与 JD 要求：
            - 列出简历中**已具备**的技能
            - 列出简历中**缺失**的技能
            - 判断**经验**是否匹配
            - 判断**学历**是否匹配

            #### 3. 计算匹配度评分
            - 匹配度 = (已匹配技能数 / 必备技能总数) × 60 + 经验匹配分(20) + 学历匹配分(20)
            - 如果有加分技能匹配，额外加 5-10 分
            - 评分范围：0-100

            #### 4. 生成优化建议
            针对缺失的技能和不足之处，给出具体可操作的优化建议：
            - 建议添加哪些关键词/技能
            - 建议突出哪些项目经历
            - 建议如何优化工作描述

            #### 5. 标注亮点
            指出简历中应该**重点突出**的内容，帮助求职者扬长避短。

            #### 5.5 按评分标准优化（必须主动贴近）
            优化目标不只是“语言更好”，而是提高 evaluateResume 的简历质量评分和 JD 匹配评分：
            - 内容繁简适中：删掉泛泛职责、重复技能、低价值堆砌；补足过短、过空、缺少上下文的经历。
            - 结果/影响导向：把“实现了某功能”改成“解决了什么问题 + 用什么关键技术 + 带来什么指标/业务结果”。
            - 学历维度：用户已提供学历、学校、专业、GPA、排名、主修课程时不要丢失，必要时整理得更清晰。
            - 链接加分：用户已提供 GitHub、Demo、项目地址、博客、作品集时必须保留，并放在面试官容易看到的位置。
            - 技术牛逼度：真实经历中的算法、高并发、分布式、性能优化、AI/RAG/Agent、复杂工程治理等要提炼为项目亮点。
            - 有 JD 时：优化后的简历必须围绕 JD 的必备技能、经验要求、业务场景和加分项调整表达；JD 相关性至少按 40% 以上重要性处理，本系统评分中固定为 45%。

            #### 6. 一页简历约束
            默认把 optimizedResume 控制为 A4 单页友好的内容密度；普通经历每段保留 2-4 个最高价值 bullet，优先删除重复、空泛、低技术含量描述。
            bullet 推荐写成 `模块亮点：基于 xxx，采用 xxx，实现 xxx，提升/降低 xxx%`，让前端模板能把冒号前小标题高亮。
            对关键技术、核心指标、延迟/成本/命中率/采纳率等重点使用 `**...**` 标记，便于前端模板加粗。
            如果候选人内容确实很强且无法压缩到 1 页，在发布 2 页版本前必须调用 AskUserQuestionTool 询问用户是否允许；未获确认时继续压缩为 1 页。

            ### 输出格式
            - 如果用户的目标是“优化简历”“生成优化结果到工作台”“顺便给我可编辑的优化版本”，优先产出 workbench artifact，而不是先给冗长 prose。
            优先调用 publishArtifact，并传入结构化参数：type="optimize_result"，optimizeResult={...}。不要把完整 JSON 再包进字符串参数。
            - 如果你已经生成了优化后的完整简历，尽量把它放进 optimizedResume 字段，避免只给分析不给可落地结果。
            - publishArtifact 成功后本轮会结束；如果需要评分，应在同一批工具调用中先调用 publishArtifact 发布 optimize_result，再调用 evaluateResume。不要计划先等 evaluateResume 返回后再发布 optimize_result。
            - 调用 evaluateResume 时生成新版 evaluation。参数 originalResumeText 使用用户原始简历提取文本，generatedResume 使用 optimizedResume 或当前工作台预览简历。
            - 只有用户明确提供 JD、岗位要求或当前优化请求包含 JD 时，才把 jobDescription 传给 evaluateResume 并生成 JD 匹配度；没有 JD 时必须走无 JD 简历评价链路，不要默认给 JD 匹配分。
            如果当前无法调用 publishArtifact，则最终只输出严格 JSON 对象本身。
            必须输出 JSON 格式，最外层固定为 {"type":"optimize_result", ...}，包含以下字段：
            - type：固定为 "optimize_result"
            - matchScore：匹配度评分（0-100 的整数）
            - matchAnalysis：匹配分析详情
            - suggestions：优化建议列表
            - highlights：建议突出的亮点
            - optimizedResume：可选，优化后的完整简历对象
            - evaluation：可选，新版简历评价与 JD 匹配度评分结果；若 evaluateResume 已返回结果，应放入该字段或单独发布 resume_evaluation artifact
            - 最终 JSON 必须严格合法：不要包裹 Markdown 代码块，不要添加说明文字；字符串内部如需英文双引号，必须转义为 \\"，或改用中文引号“”。

            完整示例：
            {"type":"optimize_result","matchScore":75,"matchAnalysis":{"matchedSkills":["Java","MySQL"],"missingSkills":["Kafka"],"experienceMatch":"符合要求"},"suggestions":["建议补充消息队列相关经验"],"highlights":["高并发系统设计经验"]}

            ### 注意事项
            - 不要编造用户没有的技能或经历
            - 建议要具体可操作，不要太笼统
            - 考虑岗位级别，给出合理的优化方向
            """;

    /**
     * 创建分析框架
     */
    private ResumeToolResult.AnalysisFramework createAnalysisFramework() {
        return ResumeToolResult.AnalysisFramework.builder()
                .mustHaveSkills(List.of())
                .bonusSkills(List.of())
                .experienceRequirement("")
                .educationRequirement("")
                .build();
    }

    /**
     * 创建输出格式说明
     */
    private ResumeToolResult.OutputFormat createOutputFormat() {
        return ResumeToolResult.OutputFormat.builder()
                .matchScore("0-100 之间的整数")
                .matchAnalysis(MatchAnalysis.builder()
                        .matchedSkills(List.of("已匹配的技能列表"))
                        .missingSkills(List.of("缺失的技能列表"))
                        .experienceMatch("经验匹配描述")
                        .build())
                .suggestions(List.of(
                        "建议1：具体可操作的优化建议",
                        "建议2：具体可操作的优化建议"
                ))
                .highlights(List.of(
                        "亮点1：建议突出的项目或经历",
                        "亮点2：建议突出的项目或经历"
                ))
                .build();
    }

    // ==================== 工具方法 ====================

    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[ResumeOptimizeGuideTool] JSON序列化失败", e);
            return toJson(ResumeToolResult.ofError("优化指南序列化失败"));
        }
    }
}
