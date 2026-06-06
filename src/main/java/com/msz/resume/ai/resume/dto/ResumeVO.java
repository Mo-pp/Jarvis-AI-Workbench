package com.msz.resume.ai.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简历数据传输对象
 *
 * <p>包含简历的完整结构化数据，用于简历生成、优化、导出等功能。
 *
 * <h2>数据结构</h2>
 * <ul>
 *   <li>{@link BasicInfo} - 基本信息（姓名、联系方式等）</li>
 *   <li>{@link JobIntention} - 求职意向</li>
 *   <li>{@link Education} - 教育经历</li>
 *   <li>{@link WorkExperience} - 工作经历</li>
 *   <li>{@link Project} - 项目经历</li>
 *   <li>{@link CampusExperience} - 校园经历</li>
 *   <li>{@link Award} - 获奖经历</li>
 *   <li>{@link Skill} - 技能特长</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 基本信息
     */
    @Builder.Default
    private BasicInfo basicInfo = new BasicInfo();

    /**
     * 求职意向
     */
    @Builder.Default
    private JobIntention jobIntention = new JobIntention();

    /**
     * 个人总结
     */
    private String summary;

    /**
     * 简历排版样式
     */
    @Builder.Default
    private ResumeStyle resumeStyle = new ResumeStyle();

    /**
     * 教育经历列表
     */
    @Builder.Default
    private List<Education> educationList = new ArrayList<>();

    /**
     * 工作经历列表
     */
    @Builder.Default
    private List<WorkExperience> workList = new ArrayList<>();

    /**
     * 项目经历列表
     */
    @Builder.Default
    private List<Project> projectList = new ArrayList<>();

    /**
     * 校园经历列表
     */
    @Builder.Default
    private List<CampusExperience> campusList = new ArrayList<>();

    /**
     * 获奖经历列表
     */
    @Builder.Default
    private List<Award> awardList = new ArrayList<>();

    /**
     * 技能特长列表
     */
    @Builder.Default
    private List<Skill> skillList = new ArrayList<>();

    // ==================== 内部类定义 ====================

    /**
     * 简历整体与分区排版样式
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeStyle implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 横向页边距，单位 px */
        private Integer pageMarginX;

        /** 纵向页边距，单位 px */
        private Integer pageMarginY;

        /** 分区排版设置，key 支持 summary/education/work/project/campus/award/skills */
        @Builder.Default
        private Map<String, ResumeSectionStyle> sections = new LinkedHashMap<>();
    }

    /**
     * 分区字号与行距
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeSectionStyle implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 字号，单位 px */
        private Double fontSize;

        /** 行高，倍数 */
        private Double lineHeight;
    }

    /**
     * 基本信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 姓名 */
        private String name;

        /** 头像URL */
        private String avatar;

        /** 求职职位 */
        private String position;

        /** 性别 */
        private String gender;

        /** 年龄 */
        private String age;

        /** 政治面貌 */
        private String political;

        /** 学历水平 */
        private String educationLevel;

        /** 工作年限 */
        private String experience;

        /** 求职状态 */
        private String status;

        /** 联系电话 */
        private String phone;

        /** 电子邮箱 */
        private String email;

        /** 所在城市 */
        private String location;
    }

    /**
     * 求职意向
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobIntention implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 期望职位 */
        private String position;

        /** 期望城市 */
        private String city;

        /** 期望薪资 */
        private String salary;

        /** 到岗时间 */
        private String entryTime;
    }

    /**
     * 教育经历
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Education implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 学校名称 */
        private String school;

        /** 专业 */
        private String major;

        /** 学历 */
        private String degree;

        /** 开始时间 */
        private String startDate;

        /** 结束时间 */
        private String endDate;

        /** 描述（主修课程、成就等） */
        private String description;

        /** GPA */
        private String gpa;

        /** 排名 */
        private String rank;
    }

    /**
     * 工作经历
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkExperience implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 公司名称 */
        private String company;

        /** 部门 */
        private String department;

        /** 职位 */
        private String position;

        /** 开始时间 */
        private String startDate;

        /** 结束时间 */
        private String endDate;

        /** 工作描述（支持多行，用 \\n 分隔） */
        private String description;
    }

    /**
     * 项目经历
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Project implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 项目名称 */
        private String name;

        /** 担任角色 */
        private String role;

        /** 技术栈 */
        private String techStack;

        /** 项目地址、GitHub、在线演示等链接 */
        private String links;

        /** 开始时间 */
        private String startDate;

        /** 结束时间 */
        private String endDate;

        /** 项目描述 */
        private String description;
    }

    /**
     * 校园经历
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampusExperience implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 组织名称 */
        private String organization;

        /** 职位 */
        private String position;

        /** 开始时间 */
        private String startDate;

        /** 结束时间 */
        private String endDate;

        /** 描述 */
        private String description;
    }

    /**
     * 获奖经历
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Award implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 奖项名称 */
        private String name;

        /** 获奖时间 */
        private String date;

        /** 描述 */
        private String description;
    }

    /**
     * 技能特长
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skill implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 技能名称 */
        private String name;

        /** 掌握程度 */
        private String level;

        /** 描述 */
        private String description;
    }
}
