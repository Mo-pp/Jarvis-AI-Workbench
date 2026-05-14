package com.msz.resume.ai.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * JD 匹配分析
 *
 * <p>包含简历与 JD 的匹配详情，包括已匹配技能、缺失技能和经验匹配情况。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchAnalysis implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 已匹配的技能列表
     * 简历中已具备且 JD 要求的技能
     */
    @Builder.Default
    private List<String> matchedSkills = List.of();

    /**
     * 缺失的技能列表
     * JD 要求但简历中未体现的技能
     */
    @Builder.Default
    private List<String> missingSkills = List.of();

    /**
     * 经验匹配描述
     * 如："符合要求"、"部分符合"、"不符合"
     */
    private String experienceMatch;

    /**
     * 学历匹配描述
     */
    private String educationMatch;

    /**
     * 其他加分项匹配
     */
    @Builder.Default
    private List<String> matchedBonus = List.of();
}
