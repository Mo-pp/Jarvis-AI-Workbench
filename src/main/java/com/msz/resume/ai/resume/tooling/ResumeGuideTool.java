package com.msz.resume.ai.resume.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.resume.dto.ResumeToolResult;
import com.msz.resume.ai.resume.dto.ResumeVO;
import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 简历生成指南工具（延迟工具）
 *
 * <p>返回简历生成指南、JSON 模板和示例，帮助主 LLM 生成结构化简历。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>工具只返回静态指南，不调用 LLM</li>
 *   <li>主 LLM 根据指南自己生成简历内容</li>
 *   <li>节省 token，保持主 LLM 控制权</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <p>当用户说"帮我创建简历"、"帮我生成一份简历"时，主 LLM 调用此工具获取生成指南。
 */
@Slf4j
@CoreTool
@Component
public class ResumeGuideTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 获取简历生成指南
     *
     * <p>返回包含以下内容的指南：
     * <ul>
     *   <li>instruction - 详细的生成指南和润色原则</li>
     *   <li>template - 空的 JSON 模板</li>
     *   <li>example - 填充好的示例简历</li>
     * </ul>
     *
     * @return JSON 格式的生成指南
     */
    @Tool("获取简历生成指南。当用户需要创建、重写、生成简历，或要求放到工作台/预览/导出前先生成结构化简历时调用。若当前对话和上传内容已足够生成简历，拿到指南后应尽快调用 publishArtifact 生成 resume artifact，不要先输出大段纯文本简历草稿，除非用户明确要求纯文本版本。")
    public String getResumeGuide() {
        log.info("[ResumeGuideTool] 返回简历生成指南");

        ResumeToolResult result = ResumeToolResult.ofResumeGuide(
                INSTRUCTION,
                createTemplate(),
                createExample()
        );

        return toJson(result);
    }

    // ==================== 生成指南内容 ====================

    /**
     * 简历生成指南说明
     */
    private static final String INSTRUCTION = """
            ## 简历生成指南

            你是一位专业的简历撰写专家，请根据用户提供的信息生成结构化简历。

            ### 交互边界（必须遵守）
            - 如果当前对话里已经有足够的简历信息，默认流程应是：整理信息 → 生成结构化 resume → 立即调用 publishArtifact，把简历放到工作台。
            - 用户提到“生成简历到工作台”“放到工作台”“生成预览”“我来导出/下载”时，优先目标是工作台 artifact，不是先写一大段正文。
            - 除非用户明确要求“先给我纯文本版本/先重写成正文”，否则不要先输出整份长篇简历草稿再等待用户二次要求工作台。
            - 生成简历时，优先调用 publishArtifact，并传入结构化参数：type="resume"，resume={...}。不要把整份 JSON 再包进字符串参数。
            - 如果当前无法调用 publishArtifact，最终只输出结构化 JSON：{"type":"resume","resume":{...}}，不要在 JSON 前后添加说明文字。
            - 最终 JSON 必须严格合法：不要包裹 Markdown 代码块，不要写注释，不要输出伪 JSON。
            - 字符串内部如需出现英文双引号，必须转义为 \\"，或改用中文引号“”。例如不要写 "实现"思考-行动"闭环"，应写 "实现\\"思考-行动\\"闭环" 或 "实现“思考-行动”闭环"。
            - 生成简历后不要主动询问“是否导出/是否下载/是否确认”。前端会自动提供预览和下载入口。
            - 导出不是删除、支付、覆盖数据等敏感操作，不需要二次确认。
            - publishArtifact 只负责把简历产物放到工作台，不负责 PDF 导出。PDF 下载由前端工作台内置按钮直接处理。
            - publishArtifact 成功后本轮会结束；如果需要评分，应在同一批工具调用中先调用 publishArtifact 发布 resume，再调用 evaluateResume。不要计划先等 evaluateResume 返回后再发布 resume。
            - 调用 evaluateResume 时，对原始简历和 Jarvis 生成预览简历做评分；如果原始简历来自上传 PDF/Word/TXT/HTML 文件，必须把注入文件元信息里的 fileId 作为 sourceFileId 传给 evaluateResume，不要把上传文件全文复制到 originalResumeText。
            - 只有用户直接粘贴简历文本且没有 sourceFileId 时，才把原始简历文本传给 evaluateResume.originalResumeText。
            - 只有当前上下文明确包含 JD 或岗位要求时，才把 jobDescription 传给 evaluateResume，否则不要生成 JD 匹配度评分。
            - 发布到工作台后，如需补充说明，只说极简的一句结果摘要即可；不要在 artifact 之前为了“解释”先输出长篇正文。
            - 缺失字段必须留空或省略；不要输出 XXX、未命名候选人、目标职位、学校名称、公司名称、项目名称、奖项名称等占位文本。除非用户明确提供某个占位值，否则不要自行补占位。

            ### 润色原则
            1. **工作描述**：使用动词开头（负责、主导、参与、设计、实现、优化、搭建、重构）
            2. **量化成果**：尽量用数据说话（提升 xx%、处理 xx 量级、节省 xx 成本、服务 xx 用户）
            3. **突出亮点**：强调技术难点、业务价值、团队贡献
            4. **简洁明了**：每个工作经历下 3-5 个要点，每个要点 1-2 行

            ### 按评分标准生成（必须主动贴近）
            - 生成简历时要主动提高后续 evaluateResume 的质量评分，而不是只把信息搬运成模板。
            - 内容繁简适中：不要太空，也不要堆太多杂项；优先保留岗位相关、面试官一眼能判断价值的内容。
            - 结果/影响导向：每段经历优先写“解决了什么问题、用了什么关键技术、产生什么效果”，不要只写“实现了某某功能模块”。
            - 学历信息：如果用户提供学校、专业、学历、GPA、排名、主修课程等，应完整保留到教育经历或基本信息，帮助学历维度评分。
            - 链接信息：如果用户提供 GitHub、作品集、线上演示、项目地址、博客等链接，应保留在合适字段或描述中，帮助链接加分。
            - 技术亮点：真实经历里有算法、高并发、分布式、性能优化、AI/RAG/Agent、复杂工程治理等内容时，要提炼成亮点，不要淡化成普通 CRUD。
            - 如果用户提供 JD 或岗位要求，生成内容必须贴近 JD 关键词和核心职责；JD 相关性会显著影响评分。
            - 原始简历里的项目地址、GitHub、在线演示、博客、作品集、技术栈、压测指标、性能指标、QPS、P95、成本下降、采纳率等属于强事实信号，必须保留；可以优化表达，但不能删除、改弱或替换成泛化描述。
            - 项目经历支持 techStack 和 links 字段。原文出现“技术栈：...”时写入 techStack，出现“项目地址/GitHub/在线演示/项目链接”时写入 links，不要塞进 description 后丢失结构化展示。

            ### 项目经历业务化硬规则
            - 当用户要求结合 GitHub、源码、README、OpenViking 或 `viking://` 项目资源生成简历时，必须先把项目证据翻译成业务场景、用户流程、交付对象、业务价值和可实测指标，再写项目经历。
            - 每个重点项目的 bullet 至少 50% 必须以业务场景、用户动作、用户流程、交付对象或业务价值开头；不能让 Agent 状态机、工具系统、上下文压缩、缓存、框架选型、架构名词占据主要表达。
            - 架构、状态机、工具系统、缓存、上下文压缩、框架选型等只能作为支撑手段，必须服务于一个明确业务流程或用户场景。
            - bullet 优先结构：业务场景/用户动作 + 技术动作 + 结果/X占位指标。例如先写“支持用户导入项目资料并生成可编辑简历项目经历”，再写使用的检索、解析、Agent 或工程实现，最后写“使资料复用率提升 X%”。
            - README 只能作为弱证据，不得只围绕 README 或架构介绍深挖。强证据优先来自源码、API/Controller、前端页面、DTO/Entity、状态字段、任务链路、导入导出流程、权限/配置流、日志或已有简历事实。
            - 允许做“受证据约束的业务优化故事构造”：可以把源码/API/页面/数据流证据包装成更亮眼的业务场景、用户痛点、优化动作和业务结果表达，让项目经历像真实面试故事，而不是架构说明书。
            - 当项目证据缺少现成业务故事时，必须基于真实功能链路主动编造优化故事来填补简历里的业务相关表达；不要因为没有产品文档、用户数据或真实指标就退回“状态机、工具系统、上下文治理、缓存”等技术架构名词堆砌。
            - 业务优化故事的编造方式必须贴着项目事实：从“接口/页面/数据流实际支持什么用户动作”推导“减少什么重复操作、提升什么转化、缩短什么等待、改善什么采纳或评分”，而不是凭空新增不存在的业务系统或用户规模。
            - 最终简历 bullet 允许直接写 `提升 X%`、`下降 Xms`、`减少 X 次`、`转化率提升 X%`、`采纳率提升 X%`、`质量分提升 X 分` 等待用户实测替换的占位指标。
            - 严禁把未实测的 X 替换成具体数字；严禁无证据编造公司、用户规模、上线范围、收入、成本、真实 QPS、真实延迟或真实百分比。
            - 如果最终简历使用 X 占位指标，不要在项目描述里写“待实测指标/测量方法”；可在非简历正文的建议、说明或后续对话中提示用户如何实测并替换。
            - 重点项目至少形成 3 个可写入简历的 X 占位指标候选，除非项目证据确实没有可测路径。

            ### 排版控制
            - resume 可以包含 resumeStyle 字段，用于控制工作台预览和 PDF 导出排版。
            - resumeStyle.pageMarginX / pageMarginY 控制左右/上下页边距，单位 px，推荐范围 28-56。
            - resumeStyle.sections 支持 summary、education、work、project、campus、award、skills，每个分区可设置 fontSize（推荐 10.8-13.5）和 lineHeight（推荐 1.25-1.85）。
            - 当内容过密时，可以适当降低对应分区字号或行距；当内容过少时，可以适当增大行距和页边距，但不要用空字段或占位文字撑版面。

            ### 单页优先与模板适配
            - 默认生成适合 A4 单页展示的简历，不要输出冗长段落；除非用户明确要求多页，否则按 1 页控制内容密度。
            - 普通候选人每段经历保留 2-4 个最高价值 bullet，项目经历优先写“业务问题/技术动作/可量化结果”，删除低技术含量的泛泛职责。
            - project.description / work.description / campus.description 中的多条要点必须用换行拆开，让前端渲染成简历小圆点；优先使用 `· 小标题：内容` 或直接 `小标题：内容`，不要把多个亮点挤在一个长段落里。
            - bullet 推荐格式：`模块亮点：基于 xxx，采用 xxx，实现 xxx，提升/降低 xxx%`，冒号前的小标题会在前端模板中高亮。
            - 关键技术、核心指标、延迟/成本/命中率/采纳率等重点可用 `**...**` 标记，前端模板会自动加粗。
            - 如果内容非常多且确实无法压缩到 1 页，在生成最终 resume artifact 前必须调用 AskUserQuestionTool 询问用户是否允许 2 页；未获确认时继续压缩到 1 页。

            ### 个人总结
            - 默认不要生成个人总结。只有原始简历明确包含“个人总结/自我评价/个人优势”等摘要段，或用户明确要求补摘要时，才填写 summary。
            - 如果确实填写 summary，控制在 50-90 字，突出核心技能和经验亮点，与求职岗位匹配。
            - 不要为了填满版面编造 summary，把空间优先留给项目、技术栈、链接和量化成果。

            ### 技能列表
            - 技能名称 + 掌握程度（精通/熟练/熟悉/了解）
            - 按重要程度排序
            - 与岗位要求匹配
            - 技能名称和分类尽量保留原始简历结构，例如“缓存与中间件”“AI 应用开发”“工程能力”；不要随意改名导致用户原有技术栈丢失
            - 技能说明多个要点用换行符分隔；如果原文用“· 小标题：内容”，保留项目符号 `·` + 小标题结构，但严禁输出“黑点”两个字
            - 技能 level 字段已经表达掌握程度，skill.description 不要再以“精通/熟练/熟悉/了解”等掌握程度开头，避免预览中出现重复

            ### 输出格式
            必须输出 JSON 格式，结构参考 template 字段。
            最外层固定为 {"type":"resume","resume":{...}}。
            字段命名使用驼峰式（camelCase）。
            优先通过 publishArtifact 发布这个结构化对象；无法调用 publishArtifact 时，最终回答只能是这个 JSON 对象本身，不要加 Markdown fence 或解释文字。

            ### 注意事项
            - 不要编造用户没有的经历
            - 不要为了评分编造原始简历文本；图片、PDF、Word、TXT、HTML 的原始简历评分只使用当前上下文中已经提取出的文字
            - 如果信息不完整，合理推断或留空；不能把没有出现在原始简历里的状态、经验、薪资、到岗时间等写到顶部基础信息里
            - 日期格式统一为 "YYYY.MM" 或 "YYYY年MM月"
            - 工作描述多个要点用换行符分隔
            - 技能描述多个要点也用换行符分隔，不要为了省事把所有技能挤成一个长句
            """;

    /**
     * 创建空的简历模板
     */
    private ResumeVO createTemplate() {
        return ResumeVO.builder()
                .basicInfo(ResumeVO.BasicInfo.builder()
                        .name("")
                        .phone("")
                        .email("")
                        .location("")
                        .position("")
                        .experience("")
                        .build())
                .summary("")
                .resumeStyle(ResumeVO.ResumeStyle.builder().build())
                .educationList(List.of(
                        ResumeVO.Education.builder()
                                .school("")
                                .major("")
                                .degree("")
                                .startDate("")
                                .endDate("")
                                .description("")
                                .build()
                ))
                .workList(List.of(
                        ResumeVO.WorkExperience.builder()
                                .company("")
                                .position("")
                                .startDate("")
                                .endDate("")
                                .description("")
                                .build()
                ))
                .projectList(List.of(
                        ResumeVO.Project.builder()
                                .name("")
                                .role("")
                                .techStack("")
                                .links("")
                                .startDate("")
                                .endDate("")
                                .description("")
                                .build()
                ))
                .skillList(List.of(
                        ResumeVO.Skill.builder()
                                .name("")
                                .level("")
                                .build()
                ))
                .build();
    }

    /**
     * 创建示例简历
     */
    private ResumeVO createExample() {
        return ResumeVO.builder()
                .basicInfo(ResumeVO.BasicInfo.builder()
                        .name("张三")
                        .phone("13812345678")
                        .email("zhangsan@example.com")
                        .location("北京")
                        .position("Java后端开发工程师")
                        .experience("5年")
                        .build())
                .summary("")
                .resumeStyle(ResumeVO.ResumeStyle.builder()
                        .pageMarginX(44)
                        .pageMarginY(36)
                        .sections(java.util.Map.of(
                                "summary", ResumeVO.ResumeSectionStyle.builder().fontSize(12.2).lineHeight(1.62).build(),
                                "work", ResumeVO.ResumeSectionStyle.builder().fontSize(11.9).lineHeight(1.56).build(),
                                "project", ResumeVO.ResumeSectionStyle.builder().fontSize(11.9).lineHeight(1.56).build(),
                                "skills", ResumeVO.ResumeSectionStyle.builder().fontSize(11.9).lineHeight(1.58).build()
                        ))
                        .build())
                .educationList(List.of(
                        ResumeVO.Education.builder()
                                .school("北京大学")
                                .major("计算机科学与技术")
                                .degree("本科")
                                .startDate("2015.09")
                                .endDate("2019.06")
                                .description("主修课程：数据结构、算法、操作系统、计算机网络")
                                .build()
                ))
                .workList(List.of(
                        ResumeVO.WorkExperience.builder()
                                .company("阿里云")
                                .position("后端开发工程师")
                                .startDate("2022.03")
                                .endDate("至今")
                                .description("""
                                        负责云存储服务核心模块开发，日均请求量10亿+
                                        设计并实现分布式锁服务，提升系统稳定性30%
                                        优化存储引擎，降低延迟20%，节省成本500万/年""")
                                .build(),
                        ResumeVO.WorkExperience.builder()
                                .company("字节跳动")
                                .position("后端开发工程师")
                                .startDate("2019.07")
                                .endDate("2022.02")
                                .description("""
                                        参与抖音推荐系统开发，优化召回策略提升用户留存
                                        设计实时特征计算服务，支持百万级QPS""")
                                .build()
                ))
                .projectList(List.of(
                        ResumeVO.Project.builder()
                                .name("云存储服务重构")
                                .role("核心开发")
                                .techStack("Java、Spring Boot、MySQL、Redis、Kafka")
                                .links("GitHub：https://github.com/example/project")
                                .startDate("2022.06")
                                .endDate("2023.03")
                                .description("主导存储服务架构升级，引入分层存储策略，降低存储成本40%")
                                .build()
                ))
                .skillList(List.of(
                        ResumeVO.Skill.builder().name("Java").level("精通").build(),
                        ResumeVO.Skill.builder().name("Spring Boot").level("精通").build(),
                        ResumeVO.Skill.builder().name("MySQL").level("熟练").build(),
                        ResumeVO.Skill.builder().name("Redis").level("熟练").build(),
                        ResumeVO.Skill.builder().name("Kafka").level("熟悉").build(),
                        ResumeVO.Skill.builder().name("Docker").level("熟悉").build()
                ))
                .build();
    }

    // ==================== 工具方法 ====================

    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[ResumeGuideTool] JSON序列化失败", e);
            return toJson(ResumeToolResult.ofError("生成指南序列化失败"));
        }
    }
}
