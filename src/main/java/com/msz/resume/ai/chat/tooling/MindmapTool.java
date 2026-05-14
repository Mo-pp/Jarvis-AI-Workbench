package com.msz.resume.ai.chat.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 思维导图工具（延迟工具）
 *
 * <p>验证 Markdown 内容并以 JSON 信封返回，前端使用 Markmap.js 渲染交互式思维导图。
 * 支持标题层级（# ## ###）和列表格式（- *），Markmap 均可直接渲染。
 *
 * <p>延迟工具首轮只显示名称，需要时通过 toolSearch 加载完整 schema。
 */
@Slf4j
@Component
public class MindmapTool {

    /**
     * 匹配 Markdown 标题或列表项的正则表达式
     * 匹配 # 标题 或 - / * 列表项
     */
    private static final Pattern CONTENT_PATTERN = Pattern.compile(
            "^(#{1,6}\\s+|[-*]\\s+)", Pattern.MULTILINE);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 验证 Markdown 内容并返回 JSON 信封，供前端 Markmap 渲染
     *
     * <p>输入格式要求：
     * <ul>
     *   <li>支持标题格式：# 一级标题、## 二级标题、### 三级标题等</li>
     *   <li>支持列表格式：- 列表项 或 * 列表项，子层级用缩进表示</li>
     *   <li>需要至少一个标题或列表项作为有效输入</li>
     * </ul>
     *
     * <p>示例：
     * <pre>
     * # 项目计划
     * ## 需求分析
     * - 用户调研
     * - 竞品分析
     * ## 技术方案
     * </pre>
     *
     * @param markdown Markdown 格式文本，支持标题（#）和列表（- *）格式
     * @return JSON 字符串 {"type":"mindmap","markdown":"..."}，格式错误时返回错误提示
     */
    @Tool("从 Markdown 生成思维导图。输入支持 Markdown 标题（# ## ###）和列表格式（- *），层级关系自然表达。输出 JSON 格式供前端 Markmap 渲染。")
    public String generateMindmap(String markdown) {
        log.info("[MindmapTool] 开始转换, 输入长度: {}", markdown != null ? markdown.length() : 0);

        if (markdown == null || markdown.isBlank()) {
            return "错误：输入内容为空，请提供包含标题或列表的 Markdown 文本。";
        }

        if (!CONTENT_PATTERN.matcher(markdown).find()) {
            return "错误：未找到有效的 Markdown 标题或列表项。请确保输入包含 # 标题或 - 列表格式，例如：\n" +
                   "# 主题\n" +
                   "## 分支1\n" +
                   "- 子项1\n" +
                   "- 子项2";
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(Map.of("type", "mindmap", "markdown", markdown));
            log.info("[MindmapTool] 转换完成, 输出长度: {}", json.length());
            return json;
        } catch (JsonProcessingException e) {
            log.error("[MindmapTool] JSON 序列化失败", e);
            return "错误：生成思维导图失败，请重试。";
        }
    }
}
