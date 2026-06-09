package com.msz.resume.ai.chat.tooling;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 子Agent派发工具（延迟工具）
 *
 * <p>允许 LLM 将复杂子任务派发给独立的子Agent执行，
 * 子Agent在隔离的上下文中完成任务后返回结果摘要给父图。
 *
 * <p><b>重要</b>：此工具不会真正执行，而是被 {@link com.msz.resume.ai.chat.runtime.node.inner.ExecuteToolNode}
 * 拦截，创建子状态机执行子任务后返回结果。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>延迟加载</b>：无 @CoreTask 注解，通过 toolSearch 按需发现</li>
 *   <li><b>阻塞递归</b>：子Agent模式下调用 spawnAgent 会被 ExecuteToolNode 拦截并返回错误</li>
 *   <li><b>并行执行</b>：多个 spawnAgent 调用可并行执行</li>
 * </ul>
 *
 * <h2>Agent类型</h2>
 * <ul>
 *   <li><b>General</b>：通用Agent，可使用所有工具，支持读写操作。可通过allowedTools自定义工具集。</li>
 *   <li><b>Plan</b>：规划Agent，只读模式，用于探索代码库、设计实现方案。</li>
 *   <li><b>Explore</b>：探索Agent，只读模式，用于快速搜索代码、回答问题。</li>
 *   <li><b>ResumeBusinessExplore</b>：简历项目业务探索Agent，只读模式，用于返回项目证据、业务场景、用户痛点、用户流程、X占位指标和候选bullet；缺少业务故事时基于真实功能链路构造优化故事。</li>
 * </ul>
 *
 * <h2>参数说明</h2>
 * <ul>
 *   <li><b>prompt</b>：任务描述，告诉子Agent需要做什么</li>
 *   <li><b>subagentType</b>：子Agent类型（General/Plan/Explore/ResumeBusinessExplore），默认General</li>
 *   <li><b>allowedTools</b>：仅General类型可用，允许自定义工具集，逗号分隔</li>
 *   <li><b>maxTurns</b>：子Agent最大迭代轮次，默认30；ResumeBusinessExplore 默认50。主Agent应根据任务复杂度主动调整</li>
 * </ul>
 *
 * <h2>使用示例（LLM调用）</h2>
 * <pre>{@code
 * // 探索代码库（只读）
 * spawnAgent("搜索用户相关的API端点", "Explore", null, 15)
 *
 * // 设计实现方案（只读）
 * spawnAgent("设计支付模块的架构", "Plan", null, 20)
 *
 * // 通用任务（可自定义工具）
 * spawnAgent("实现用户登录功能", "General", "toolSearch,write,edit", 10)
 *
 * // 通用任务（使用所有工具）
 * spawnAgent("重构认证模块", "General", null, 10)
 * }</pre>
 */
@Slf4j
@Component
public class SpawnAgentTool {

    /**
     * 派发任务给子Agent执行
     *
     * <p>此方法不会真正调用，而是被 ExecuteToolNode 拦截处理。
     * ExecuteToolNode 会创建独立的子状态机执行子任务，返回结果摘要。
     *
     * @param prompt          任务描述，告诉子Agent需要完成什么任务
     * @param subagentType    子Agent类型：General(通用), Plan(规划), Explore(探索), ResumeBusinessExplore(简历项目业务探索)。默认General。
     * @param allowedTools    允许子Agent使用的工具名称，逗号分隔。仅当subagentType为General时可自定义，其他类型忽略此参数。
 * @param maxTurns        子Agent最大迭代轮次，默认30；ResumeBusinessExplore 默认50。主Agent应根据任务复杂度主动调整。
     * @return 不会真正返回，工具调用会被拦截
     */
    @Tool("将复杂子任务派发给子Agent独立执行。子Agent在隔离上下文中完成任务后返回结果摘要。" +
          "适用于需要多步推理、搜索多个信息源、或需要独立执行流程的任务，(为了有效防止污染主agent的上下文)。" +
          "subagentType参数选择子Agent类型：General(通用读写)、Plan(规划只读)、Explore(探索只读)、ResumeBusinessExplore(简历项目业务探索只读)。" +
          "ResumeBusinessExplore只返回证据、业务场景、用户痛点、用户流程、X占位指标和候选bullet；缺少业务故事时基于真实功能链路构造优化故事，减少架构名词堆砌，不直接生成最终简历artifact。" +
          "子Agent不可再派发任务或向用户提问。")
    public String spawnAgent(
            @P("任务描述，告诉子Agent需要完成什么任务") String prompt,
            @P("子Agent类型：General(通用读写), Plan(规划只读), Explore(探索只读), ResumeBusinessExplore(简历项目业务探索只读)。默认General。") String subagentType,
            @P("允许子Agent使用的工具名称，逗号分隔。仅General类型可自定义，其他类型忽略此参数。") String allowedTools,
            @P("子Agent最大迭代轮次，默认30；ResumeBusinessExplore默认50。简单任务可适当调低，复杂任务可按需要调高。") Integer maxTurns
    ) {
        // 此方法不会被真正调用
        // ExecuteToolNode 会拦截 spawnAgent 工具调用
        log.warn("[SpawnAgent] 工具方法被直接调用，这不应该发生");
        return "工具调用应该被 ExecuteToolNode 拦截处理";
    }
}
