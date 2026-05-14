package com.msz.resume.ai.chat.tooling;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 任务委派工具（延迟工具）
 *
 * <p>允许 LLM 将复杂子任务委派给独立的子Agent执行，
 * 子Agent在隔离的上下文中完成任务后返回结果摘要给父图。
 *
 * <p><b>重要</b>：此工具不会真正执行，而是被 {@link com.msz.resume.ai.chat.runtime.node.inner.ExecuteToolNode}
 * 拦截，创建子状态机执行子任务后返回结果。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>延迟加载</b>：无 @CoreTask 注解，通过 toolSearch 按需发现</li>
 *   <li><b>阻塞递归</b>：子Agent模式下调用 delegateTask 会被 ExecuteToolNode 拦截并返回错误</li>
 *   <li><b>并行执行</b>：多个 delegateTask 调用可并行执行</li>
 * </ul>
 *
 * <h2>Agent类型</h2>
 * <ul>
 *   <li><b>GENERAL</b>：通用Agent，可使用所有工具，支持读写操作。可通过toolNames自定义工具集。</li>
 *   <li><b>PLAN</b>：规划Agent，只读模式，用于探索代码库、设计实现方案。</li>
 *   <li><b>EXPLORE</b>：探索Agent，只读模式，用于快速搜索代码、回答问题。</li>
 * </ul>
 *
 * <h2>参数说明</h2>
 * <ul>
 *   <li><b>taskDescription</b>：任务描述，告诉子Agent需要做什么</li>
 *   <li><b>agentType</b>：Agent类型（GENERAL/PLAN/EXPLORE），默认GENERAL</li>
 *   <li><b>toolNames</b>：仅GENERAL类型可用，允许自定义工具集，逗号分隔</li>
 *   <li><b>maxTurns</b>：子Agent最大迭代轮次，默认30；主Agent应根据任务复杂度主动调整</li>
 * </ul>
 *
 * <h2>使用示例（LLM调用）</h2>
 * <pre>{@code
 * // 探索代码库（只读）
 * delegateTask("搜索用户相关的API端点", "EXPLORE", null, 15)
 *
 * // 设计实现方案（只读）
 * delegateTask("设计支付模块的架构", "PLAN", null, 20)
 *
 * // 通用任务（可自定义工具）
 * delegateTask("实现用户登录功能", "GENERAL", "toolSearch,write,edit", 10)
 *
 * // 通用任务（使用所有工具）
 * delegateTask("重构认证模块", "GENERAL", null, 10)
 * }</pre>
 */
@Slf4j
@Component
public class DelegateTaskTool {

    /**
     * 委派任务给子Agent执行
     *
     * <p>此方法不会真正调用，而是被 ExecuteToolNode 拦截处理。
     * ExecuteToolNode 会创建独立的子状态机执行子任务，返回结果摘要。
     *
     * @param taskDescription 任务描述，告诉子Agent需要完成什么任务
     * @param agentType       Agent类型：GENERAL(通用), PLAN(规划), EXPLORE(探索)。默认GENERAL。
     * @param toolNames       允许子Agent使用的工具名称，逗号分隔。仅当agentType为GENERAL时可自定义，其他类型忽略此参数。
 * @param maxTurns        子Agent最大迭代轮次，默认30。主Agent应根据任务复杂度主动调整。
     * @return 不会真正返回，工具调用会被拦截
     */
    @Tool("将复杂子任务委派给子Agent独立执行。子Agent在隔离上下文中完成任务后返回结果摘要。" +
          "适用于需要多步推理、搜索多个信息源、或需要独立执行流程的任务。" +
          "agentType参数选择Agent类型：GENERAL(通用读写)、PLAN(规划只读)、EXPLORE(探索只读)。" +
          "子Agent不可再委派任务或向用户提问。")
    public String delegateTask(
            @P("任务描述，告诉子Agent需要完成什么任务") String taskDescription,
            @P("Agent类型：GENERAL(通用读写), PLAN(规划只读), EXPLORE(探索只读)。默认GENERAL。") String agentType,
            @P("允许子Agent使用的工具名称，逗号分隔。仅GENERAL类型可自定义，其他类型忽略此参数。") String toolNames,
            @P("子Agent最大迭代轮次，默认30。简单任务可适当调低，复杂任务可按需要调高。") Integer maxTurns
    ) {
        // 此方法不会被真正调用
        // ExecuteToolNode 会拦截 delegateTask 工具调用
        log.warn("[DelegateTask] 工具方法被直接调用，这不应该发生");
        return "工具调用应该被 ExecuteToolNode 拦截处理";
    }
}
