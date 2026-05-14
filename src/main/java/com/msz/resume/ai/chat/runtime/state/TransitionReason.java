package com.msz.resume.ai.chat.runtime.state;

/**
 * 状态转移原因枚举
 *
 * <p>定义内层循环中所有可能的状态转移原因，用于类型安全的状态流转。
 *
 * <h2>转移类型说明</h2>
 * <ul>
 *   <li><b>Normal</b>: 正常流转，LLM返回后继续处理</li>
 *   <li><b>Error</b>: 异常流转，进入错误恢复</li>
 *   <li><b>Retry</b>: 重试流转，错误恢复后重试</li>
 *   <li><b>Terminate</b>: 终止流转，结束循环</li>
 *   <li><b>ToolExecutedSuccess</b>: 工具执行成功</li>
 *   <li><b>ToolExecutedFailed</b>: 工具执行失败</li>
 *   <li><b>PendingUserInput</b>: 等待用户输入（AskUserQuestion）</li>
 * </ul>
 */
public enum TransitionReason {

    Normal("normal"),
    Error("error"),
    Retry("retry"),
    Terminate("terminate"),
    ToolExecutedSuccess("tool_executed_success"),
    ToolExecutedFailed("tool_executed_failed"),
    PendingUserInput("pending_user_input");

    private final String value;

    TransitionReason(String value) {
        this.value = value;
    }

    /**
     * 获取字符串值（用于存储到状态中）
     */
    public String getValue() {
        return value;
    }

    /**
     * 从字符串值解析枚举
     *
     * @param value 字符串值
     * @return 对应的枚举值，未找到则抛出异常
     */
    public static TransitionReason fromValue(String value) {
        for (TransitionReason reason : values()) {
            if (reason.value.equals(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown transition reason: " + value);
    }

    /**
     * 安全解析，未找到返回 null
     */
    public static TransitionReason fromValueOrNull(String value) {
        for (TransitionReason reason : values()) {
            if (reason.value.equals(value)) {
                return reason;
            }
        }
        return null;
    }
}
