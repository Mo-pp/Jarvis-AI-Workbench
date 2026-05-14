package com.msz.resume.ai.chat.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MindmapTool 单元测试
 *
 * 测试 Markdown 转 JSON 信封的各种场景，前端使用 Markmap.js 渲染
 */
class MindmapToolTest {

    private MindmapTool mindmapTool;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mindmapTool = new MindmapTool();
    }

    // ==================== 标题格式测试 ====================

    @Test
    @DisplayName("标题输入 - 返回有效 JSON 信封")
    void testHeadingReturnsJsonEnvelope() throws Exception {
        String markdown = "# 主题";

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertEquals(markdown, json.get("markdown").asText());
    }

    @Test
    @DisplayName("多层级标题 - 返回 JSON 信封")
    void testMultiLevelHeading() throws Exception {
        String markdown = """
                # 主题
                ## 分支1
                ### 子分支1-1
                ### 子分支1-2
                ## 分支2
                ### 子分支2-1
                """;

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertTrue(json.get("markdown").asText().contains("分支1"));
        assertTrue(json.get("markdown").asText().contains("子分支1-1"));
    }

    @Test
    @DisplayName("深层嵌套 - 6层标题")
    void testDeepNesting() throws Exception {
        String markdown = """
                # 一级
                ## 二级
                ### 三级
                #### 四级
                ##### 五级
                ###### 六级
                """;

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertTrue(json.get("markdown").asText().contains("###### 六级"));
    }

    // ==================== 列表格式测试 ====================

    @Test
    @DisplayName("列表输入 - 返回 JSON 信封")
    void testListReturnsJsonEnvelope() throws Exception {
        String markdown = """
                - 项目计划
                  - 需求分析
                    - 用户调研
                  - 技术方案
                """;

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertTrue(json.get("markdown").asText().contains("需求分析"));
    }

    @Test
    @DisplayName("星号列表输入")
    void testAsteriskList() throws Exception {
        String markdown = """
                # 主题
                * 列表项1
                * 列表项2
                """;

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
    }

    // ==================== 混合格式测试 ====================

    @Test
    @DisplayName("标题+列表混合输入")
    void testMixedHeadingAndList() throws Exception {
        String markdown = """
                # 项目计划
                ## 需求分析
                - 用户调研
                - 竞品分析
                ## 技术方案
                - 架构设计
                - 技术选型
                """;

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertTrue(json.get("markdown").asText().contains("用户调研"));
    }

    // ==================== 错误处理测试 ====================

    @Test
    @DisplayName("null 输入 - 返回错误")
    void testNullInput() {
        String result = mindmapTool.generateMindmap(null);

        assertTrue(result.contains("错误"));
        assertTrue(result.contains("空"));
    }

    @Test
    @DisplayName("空字符串 - 返回错误")
    void testEmptyInput() {
        String result = mindmapTool.generateMindmap("");

        assertTrue(result.contains("错误"));
    }

    @Test
    @DisplayName("纯空白字符 - 返回错误")
    void testBlankInput() {
        String result = mindmapTool.generateMindmap("   \n\t  ");

        assertTrue(result.contains("错误"));
    }

    @Test
    @DisplayName("纯文本无标题列表 - 返回错误")
    void testPlainTextNoHeading() {
        String markdown = "这是普通文本\n没有标题格式";

        String result = mindmapTool.generateMindmap(markdown);

        assertTrue(result.contains("错误"));
        assertTrue(result.contains("未找到有效的"));
    }

    @Test
    @DisplayName("只有二级标题 - 现在应该正常输出 JSON")
    void testOnlySecondLevelHeadings() throws Exception {
        String markdown = """
                ## 分支1
                ### 子分支
                ## 分支2
                """;

        String result = mindmapTool.generateMindmap(markdown);

        // 改造后：只要包含有效标题或列表项就输出 JSON，不再要求必须有 h1
        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertTrue(json.get("markdown").asText().contains("分支1"));
    }

    // ==================== JSON 安全性测试 ====================

    @Test
    @DisplayName("特殊字符 - 引号能正确 JSON 转义")
    void testSpecialCharactersQuotes() throws Exception {
        String markdown = "# 这是一个\"引号\"测试\n## 分支\"1\"";

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertTrue(json.get("markdown").asText().contains("\"引号\""));
    }

    @Test
    @DisplayName("特殊字符 - 反斜杠能正确 JSON 转义")
    void testSpecialCharactersBackslash() throws Exception {
        String markdown = "# 路径 C:\\Users\\test";

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
    }

    @Test
    @DisplayName("特殊字符 - 中英文混合")
    void testSpecialCharactersMixedLanguage() throws Exception {
        String markdown = """
                # Project 项目
                ## Backend 后端
                ### API Design 接口设计
                """;

        String result = mindmapTool.generateMindmap(markdown);

        JsonNode json = OBJECT_MAPPER.readTree(result);
        assertEquals("mindmap", json.get("type").asText());
        assertTrue(json.get("markdown").asText().contains("Project 项目"));
    }
}
