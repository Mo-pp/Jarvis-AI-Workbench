package com.msz.resume.ai.resume.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 简历工具统一返回结果
 *
 * <p>所有简历工具返回此格式的 JSON，便于前端和主 LLM 解析处理。
 *
 * <h2>返回类型</h2>
 * <ul>
 *   <li><b>resume_guide</b> - 简历生成指南，包含 instruction、template、example</li>
 *   <li><b>optimize_guide</b> - 优化指南，包含 instruction、analysisFramework、outputFormat</li>
 *   <li><b>error</b> - 错误信息</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>工具只返回指南和模板，主 LLM 根据指南自己生成内容。
 * 这样设计的好处：
 * <ul>
 *   <li>不需要二次调用 LLM，节省 token</li>
 *   <li>主 LLM 保持控制权</li>
 *   <li>工具响应快速（静态内容）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeToolResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 结果类型
     * <ul>
     *   <li>resume_guide - 简历生成指南</li>
     *   <li>optimize_guide - 优化指南</li>
 *   <li>error - 错误</li>
     * </ul>
     */
    private String type;

    // ==================== resume_guide 字段 ====================

    /**
     * 生成/优化指南说明
     * 告诉主 LLM 如何生成简历或分析匹配度
     */
    private String instruction;

    /**
     * JSON 模板
     * 简历数据的空模板，主 LLM 填充内容
     */
    private ResumeVO template;

    /**
     * 示例简历
     * 帮助主 LLM 理解期望输出格式
     */
    private ResumeVO example;

    // ==================== optimize_guide 字段 ====================

    /**
     * 分析框架
     * 包含 mustHaveSkills、bonusSkills 等分析维度
     */
    private AnalysisFramework analysisFramework;

    /**
     * 输出格式说明
     * 告诉主 LLM 优化结果的输出格式
     */
    private OutputFormat outputFormat;

    // ==================== error 字段 ====================

    /**
     * 错误消息
     */
    private String message;

    // ==================== 内部类 ====================

    /**
     * 分析框架
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisFramework implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private List<String> mustHaveSkills;
        private List<String> bonusSkills;
        private String experienceRequirement;
        private String educationRequirement;
    }

    /**
     * 输出格式说明
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputFormat implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String matchScore;
        private MatchAnalysis matchAnalysis;
        private List<String> suggestions;
        private List<String> highlights;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建简历生成指南
     */
    public static ResumeToolResult ofResumeGuide(String instruction, ResumeVO template, ResumeVO example) {
        return ResumeToolResult.builder()
                .type("resume_guide")
                .instruction(instruction)
                .template(template)
                .example(example)
                .build();
    }

    /**
     * 创建优化指南
     */
    public static ResumeToolResult ofOptimizeGuide(String instruction, AnalysisFramework framework, OutputFormat format) {
        return ResumeToolResult.builder()
                .type("optimize_guide")
                .instruction(instruction)
                .analysisFramework(framework)
                .outputFormat(format)
                .build();
    }

    /**
     * 创建错误结果
     */
    public static ResumeToolResult ofError(String message) {
        return ResumeToolResult.builder()
                .type("error")
                .message(message)
                .build();
    }
}
