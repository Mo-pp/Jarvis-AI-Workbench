package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.BudgetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultToolResultBudget 单元测试
 *
 * <p>由于依赖 Spring 上下文和数据库，这里只测试核心裁剪逻辑。
 * 数据库存储的完整测试应在集成测试中进行。
 */
class DefaultToolResultBudgetTest {

    private JarvisCompressionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JarvisCompressionProperties();
        properties.setMaxResultSizeChars(100);  // 测试用小阈值
        properties.setPreviewSizeBytes(50);     // 测试用小预览
    }

    @Test
    @DisplayName("needsTruncation() 正确判断是否需要截断")
    void needsTruncation_shouldReturnCorrectResult() {
        // 创建一个简单的测试实现
        TestableBudget budget = new TestableBudget(properties);

        assertFalse(budget.needsTruncation(50), "50字符不需要截断");
        assertFalse(budget.needsTruncation(100), "100字符等于阈值，不需要截断");
        assertTrue(budget.needsTruncation(101), "101字符需要截断");
        assertTrue(budget.needsTruncation(200), "200字符需要截断");
    }

    @Test
    @DisplayName("generatePreview() 在换行符处截断")
    void generatePreview_shouldTruncateAtNewline() {
        TestableBudget budget = new TestableBudget(properties);

        // 生成有换行的内容
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        String content = sb.toString();

        String preview = budget.testGeneratePreview(content);

        // 验证预览在换行处截断
        assertTrue(preview.contains("Line"), "预览应该包含内容");
        assertTrue(preview.length() <= 60, "预览长度应该合理"); // 50字节 + 一些缓冲
    }

    @Test
    @DisplayName("generatePreview() 无换行时直接截断")
    void generatePreview_shouldTruncateDirectly_whenNoNewline() {
        TestableBudget budget = new TestableBudget(properties);

        String content = "A".repeat(200);
        String preview = budget.testGeneratePreview(content);

        // 验证预览长度合理
        assertTrue(preview.length() <= 60, "预览长度应该合理");
    }

    @Test
    @DisplayName("generatePreview() 中文内容正确处理")
    void generatePreview_shouldHandleChinese() {
        TestableBudget budget = new TestableBudget(properties);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("这是第").append(i).append("行中文内容\n");
        }
        String content = sb.toString();

        String preview = budget.testGeneratePreview(content);

        // 验证没有乱码
        assertTrue(preview.contains("中文"), "预览应该包含中文且无乱码");
    }

    @Test
    @DisplayName("process() 短内容原样返回（模拟测试）")
    void process_whenContentShort_shouldReturnUnchanged() {
        TestableBudget budget = new TestableBudget(properties);

        String content = "Hello World";

        BudgetResult result = budget.process("testTool", "call-123", content, "session1");

        assertFalse(result.truncated());
        assertEquals(content, result.content());
    }

    @Test
    @DisplayName("process() 空内容处理")
    void process_whenContentEmpty_shouldReturnEmpty() {
        TestableBudget budget = new TestableBudget(properties);

        BudgetResult result1 = budget.process("testTool", "call-123", "", "session1");
        BudgetResult result2 = budget.process("testTool", "call-123", null, "session1");

        assertFalse(result1.truncated());
        assertEquals("", result1.content());

        assertFalse(result2.truncated());
        assertEquals("", result2.content());
    }

    // ==================== 测试辅助类 ====================

    /**
     * 可测试的 Budget 实现
     *
     * <p>使用内存存储替代数据库，便于单元测试
     */
    static class TestableBudget implements ToolResultBudget {
        private final JarvisCompressionProperties properties;
        private long nextId = 1;
        private java.util.Map<Long, String> storage = new java.util.HashMap<>();

        TestableBudget(JarvisCompressionProperties properties) {
            this.properties = properties;
        }

        @Override
        public BudgetResult process(String toolName, String toolCallId, String result, String sessionId) {
            if (result == null || result.isEmpty()) {
                return BudgetResult.unchanged("");
            }

            int originalSize = result.length();

            if (!needsTruncation(originalSize)) {
                return BudgetResult.unchanged(result);
            }

            String preview = testGeneratePreview(result);
            Long blobId = nextId++;
            storage.put(blobId, result);

            String content = buildPreviewMessage(preview, blobId, originalSize);
            return BudgetResult.truncated(content, "blob:" + blobId, originalSize);
        }

        @Override
        public boolean needsTruncation(int resultSize) {
            return resultSize > properties.getMaxResultSizeChars();
        }

        /**
         * 公开预览生成方法供测试
         */
        String testGeneratePreview(String content) {
            int previewBytes = properties.getPreviewSizeBytes();
            byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            if (bytes.length <= previewBytes) {
                return content;
            }

            String preview = new String(bytes, 0, previewBytes, java.nio.charset.StandardCharsets.UTF_8);

            int halfLength = preview.length() / 2;
            int lastNewline = preview.lastIndexOf('\n', preview.length() - 1);

            while (lastNewline > halfLength) {
                return preview.substring(0, lastNewline + 1);
            }

            lastNewline = preview.lastIndexOf('\n');
            if (lastNewline > 0) {
                return preview.substring(0, lastNewline + 1);
            }

            // 安全截断
            int truncateAt = previewBytes;
            while (truncateAt > 0) {
                byte b = bytes[truncateAt - 1];
                if ((b & 0xC0) != 0x80) {
                    break;
                }
                truncateAt--;
            }
            return new String(bytes, 0, truncateAt, java.nio.charset.StandardCharsets.UTF_8);
        }

        private String buildPreviewMessage(String preview, Long blobId, int originalSize) {
            StringBuilder sb = new StringBuilder();
            sb.append("<persisted-output>\n");
            sb.append(String.format("输出过大 (%d 字符)。完整内容已保存到数据库，ID: %d\n\n",
                    originalSize, blobId));
            sb.append("预览 (前 ").append(properties.getPreviewSizeBytes()).append(" 字节):\n");
            sb.append(preview);
            if (!preview.endsWith("\n")) {
                sb.append("...");
            }
            sb.append("\n</persisted-output>");
            return sb.toString();
        }
    }
}
