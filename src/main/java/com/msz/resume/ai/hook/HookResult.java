package com.msz.resume.ai.hook;

/**
 * Hook 执行结果
 *
 * <p>工具调用拦截点（PreToolUse / PostToolUse）的返回值。
 * 三种结果：
 * <ul>
 *   <li>{@link #continueResult()} — 放行，继续执行下一个 Hook 或工具本身</li>
 *   <li>{@link #block(String)} — 阻断，终止 Hook 链并取消工具执行</li>
 *   <li>{@link #modifyArgs(java.util.Map)} — 修改参数后放行，替换工具的入参</li>
 * </ul>
 *
 * <h2>设计参考</h2>
 * Claude Code 的 Hook 系统使用 exit code 0/2 + stdout JSON 控制流程。
 * JARVIS 的 HookResult 是其 Java 等价物：用类型安全的工厂方法替代 exit code。
 *
 * @see HookService
 */
public sealed interface HookResult {

    /**
     * 是否为阻断结果
     */
    boolean isBlocked();

    /**
     * 阻断原因（仅 block 结果有值）
     */
    String blockReason();

    /**
     * 修改后的参数（仅 modifyArgs 结果有值）
     */
    java.util.Map<String, Object> modifiedArgs();

    // ========== 工厂方法 ==========

    /**
     * 放行：继续执行下一个 Hook 或工具本身
     */
    static HookResult continueResult() {
        return Continue.INSTANCE;
    }

    /**
     * 阻断：终止 Hook 链，取消工具执行
     *
     * @param reason 阻断原因，会作为 ToolExecutionResultMessage 返回给 LLM
     */
    static HookResult block(String reason) {
        return new Block(reason);
    }

    /**
     * 修改参数后放行：替换工具的入参，继续执行
     *
     * @param newArgs 修改后的参数 Map
     */
    static HookResult modifyArgs(java.util.Map<String, Object> newArgs) {
        return new ModifyArgs(newArgs);
    }

    // ========== 内部实现 ==========

    /**
     * 放行结果（单例）
     */
    final class Continue implements HookResult {
        static final Continue INSTANCE = new Continue();

        private Continue() {}

        @Override public boolean isBlocked() { return false; }
        @Override public String blockReason() { return null; }
        @Override public java.util.Map<String, Object> modifiedArgs() { return null; }

        @Override public String toString() { return "HookResult.continueResult()"; }
    }

    /**
     * 阻断结果
     */
    final class Block implements HookResult {
        private final String reason;

        Block(String reason) {
            this.reason = reason;
        }

        @Override public boolean isBlocked() { return true; }
        @Override public String blockReason() { return reason; }
        @Override public java.util.Map<String, Object> modifiedArgs() { return null; }

        @Override public String toString() { return "HookResult.block(" + reason + ")"; }
    }

    /**
     * 修改参数结果
     */
    final class ModifyArgs implements HookResult {
        private final java.util.Map<String, Object> args;

        ModifyArgs(java.util.Map<String, Object> args) {
            this.args = args;
        }

        @Override public boolean isBlocked() { return false; }
        @Override public String blockReason() { return null; }
        @Override public java.util.Map<String, Object> modifiedArgs() { return args; }

        @Override public String toString() { return "HookResult.modifyArgs(" + args + ")"; }
    }
}
