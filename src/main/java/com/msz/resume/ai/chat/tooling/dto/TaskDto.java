package com.msz.resume.ai.chat.tooling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务 DTO
 *
 * <p>表示任务规划中的单个任务，包含描述、状态和时间戳。
 *
 * <h2>状态流转</h2>
 * <ul>
 *   <li><b>pending</b>: 待执行</li>
 *   <li><b>in_progress</b>: 进行中（同一时间只有一个任务处于此状态）</li>
 *   <li><b>completed</b>: 已完成</li>
 *   <li><b>skipped</b>: 已跳过</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务唯一标识
     * 由 TaskPlanTool 自动生成，格式: task-1, task-2, ...
     */
    private String taskId;

    /**
     * 任务简短描述
     * 概括该任务要做什么
     */
    private String description;

    /**
     * 任务详细说明
     * 解释为什么要做这个任务、怎么做
     */
    private String detail;

    /**
     * 任务状态
     * pending / in_progress / completed / skipped
     */
    @Builder.Default
    private String status = "pending";

    /**
     * 创建时间戳（毫秒）
     */
    private Long createdAt;

    /**
     * 最后更新时间戳（毫秒）
     */
    private Long updatedAt;
}
