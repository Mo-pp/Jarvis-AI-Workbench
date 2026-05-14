package com.msz.resume.ai.chat.compression.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BudgetResult 单元测试
 */
class BudgetResultTest {

    @Test
    @DisplayName("unchanged() 创建未改变的结果")
    void unchanged_shouldCreateResultWithTruncatedFalse() {
        String content = "Hello World";

        BudgetResult result = BudgetResult.unchanged(content);

        assertEquals(content, result.content());
        assertFalse(result.truncated());
        assertNull(result.persistedPath());
        assertEquals(content.length(), result.originalSize());
        assertEquals(content.length(), result.resultSize());
    }

    @Test
    @DisplayName("unchanged() 处理null内容")
    void unchanged_whenNull_shouldReturnEmpty() {
        BudgetResult result = BudgetResult.unchanged(null);

        assertEquals("", result.content());
        assertEquals(0, result.originalSize());
        assertEquals(0, result.resultSize());
    }

    @Test
    @DisplayName("truncated() 创建截断结果")
    void truncated_shouldCreateResultWithTruncatedTrue() {
        String preview = "Preview content...";
        String path = "/tmp/tool-results/fileRead_123.txt";

        BudgetResult result = BudgetResult.truncated(preview, path, 100000);

        assertEquals(preview, result.content());
        assertTrue(result.truncated());
        assertEquals(path, result.persistedPath());
        assertEquals(100000, result.originalSize());
        assertEquals(preview.length(), result.resultSize());
    }

    @Test
    @DisplayName("charsSaved() 正确计算节省字符数")
    void charsSaved_shouldCalculateCorrectly() {
        BudgetResult result = BudgetResult.truncated("Preview", "/path", 10000);

        assertEquals(10000 - 7, result.charsSaved());
    }
}
