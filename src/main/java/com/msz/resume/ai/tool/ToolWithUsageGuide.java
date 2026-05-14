package com.msz.resume.ai.tool;

/**
 * 工具使用指南提供者接口
 *
 * <p>实现此接口的工具可以提供使用指南，该指南会被自动注入到 LLM 的系统提示词中，
 * 帮助模型更准确地理解和使用工具。
 *
 * <p>使用示例：
 * <pre>{@code
 * @Component
 * public class MyTool implements ToolWithUsageGuide {
 *
 *     @Tool("工具描述")
 *     public String doSomething(String input) { ... }
 *
 *     @Override
 *     public String getUsageGuide() {
 *         return "使用此工具时请注意：...";
 *     }
 * }
 * }</pre>
 *
 * @author Jarvis Team
 * @since 1.0
 */
public interface ToolWithUsageGuide {

    /**
     * 获取工具使用指南
     *
     * <p>返回的内容会被拼接到系统提示词中，指导 LLM 如何正确使用此工具。
     *
     * @return 使用指南文本，返回 null 表示无特殊说明
     */
    default String getUsageGuide() {
        return null;
    }
}
