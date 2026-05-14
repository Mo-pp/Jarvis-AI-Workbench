package com.msz.resume.ai.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 简历优化结果
 *
 * <p>包含 JD 匹配度分析、优化建议和优化后的简历数据。
 *
 * <h2>数据结构</h2>
 * <ul>
 *   <li>matchScore - 匹配度评分（0-100）</li>
 *   <li>matchAnalysis - 匹配分析详情</li>
 *   <li>suggestions - 优化建议列表</li>
 *   <li>optimizedResume - 优化后的简历</li>
 *   <li>highlights - 建议突出的亮点</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 匹配度评分
     * 范围：0-100
     * 0-40: 低匹配度
     * 41-70: 中等匹配度
     * 71-100: 高匹配度
     */
    private Integer matchScore;

    /**
     * 匹配分析详情
     */
    @Builder.Default
    private MatchAnalysis matchAnalysis = new MatchAnalysis();

    /**
     * 优化建议列表
     * 每条建议为可操作的具体指导
     */
    @Builder.Default
    private List<String> suggestions = List.of();

    /**
     * 优化后的简历数据
     * 基于原简历和 JD 分析结果优化
     */
    private ResumeVO optimizedResume;

    /**
     * 建议突出的亮点
     * 指出简历中应该重点强调的内容
     */
    @Builder.Default
    private List<String> highlights = List.of();
}
