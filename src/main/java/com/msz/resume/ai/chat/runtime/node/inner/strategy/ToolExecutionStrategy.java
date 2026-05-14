package com.msz.resume.ai.chat.runtime.node.inner.strategy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * 工具执行策略接口
 *
 * <p>将不同类型工具的执行逻辑抽象为策略，实现职责分离。
 * 每种特殊工具（如 spawnAgent、askUserQuestion）可独立实现执行逻辑。
 *
 * <p>设计原则：
 * <ul>
 *   <li>单一职责：每个策略只负责一类工具的执行</li>
 *   <li>开闭原则：新增工具类型只需添加新策略，无需修改协调者</li>
 *   <li>可测试性：策略可独立单元测试</li>
 * </ul>
 */
public interface ToolExecutionStrategy {

    /**
     * 判断是否支持处理该工具请求
     *
     * @param request 工具执行请求
     * @return true 表示支持处理
     */
    boolean supports(ToolExecutionRequest request);

    /**
     * 执行工具
     *
     * @param context 执行上下文，包含状态和请求列表
     * @return 执行结果
     */
    ToolExecutionResult execute(ToolExecutionContext context);

    /**
     * 策略优先级（数字越小优先级越高）
     *
     * <p>当多个策略都支持同一请求时，选择优先级最高的。
     * 默认优先级为 100，特殊工具策略应设置更小的值。
     *
     * @return 优先级值
     */
    default int getPriority() {
        return 100;
    }
}
