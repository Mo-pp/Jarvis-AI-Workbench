package com.msz.resume.ai.tool.registry;

/**
 * 延迟工具的轻量描述
 *
 * 仅包含 name + description，用于 toolSearch 返回结果
 * 不包含完整的参数 schema，减少 token 消耗
 *
 * @param name 工具名称
 * @param description 工具描述
 */
public record ToolHint(String name, String description) {
}
