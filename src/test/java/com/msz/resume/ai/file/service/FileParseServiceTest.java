package com.msz.resume.ai.file.service;

import com.msz.resume.ai.file.dto.ParsedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件解析服务单元测试
 */
class FileParseServiceTest {

    private FileParseService fileParseService;

    @BeforeEach
    void setUp() {
        fileParseService = new FileParseService();
    }

    @Test
    @DisplayName("解析 TXT 文件成功")
    void testParseTxtFile() {
        String content = "这是一段测试文本。\n包含多行内容。";
        byte[] fileData = content.getBytes(StandardCharsets.UTF_8);

        ParsedFile result = fileParseService.parse(fileData, "test.txt");

        assertTrue(result.isSuccess());
        assertEquals("txt", result.getFileType());
        assertEquals("test.txt", result.getFileName());
        assertEquals(content, result.getContent());
        assertNotNull(result.getFileId());
    }

    @Test
    @DisplayName("解析 HTML 文件成功")
    void testParseHtmlFile() {
        String html = "<html><body><h1>标题</h1><p>这是一段HTML内容。</p></body></html>";
        byte[] fileData = html.getBytes(StandardCharsets.UTF_8);

        ParsedFile result = fileParseService.parse(fileData, "test.html");

        assertTrue(result.isSuccess());
        assertEquals("html", result.getFileType());
        assertTrue(result.getContent().contains("标题"));
        assertTrue(result.getContent().contains("这是一段HTML内容"));
        assertFalse(result.getContent().contains("<html>")); // HTML 标签应被移除
    }

    @Test
    @DisplayName("不支持大文件（超过 15MB）")
    void testRejectLargeFile() {
        // 创建一个超过 15MB 的字节数组
        byte[] largeFileData = new byte[16 * 1024 * 1024];

        ParsedFile result = fileParseService.parse(largeFileData, "large.txt");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("文件大小超过限制"));
    }

    @Test
    @DisplayName("不支持的文件类型返回错误")
    void testUnsupportedFileType() {
        byte[] fileData = "test content".getBytes(StandardCharsets.UTF_8);

        ParsedFile result = fileParseService.parse(fileData, "test.xyz");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("不支持的文件类型"));
    }

    @Test
    @DisplayName("isSupported 方法正常工作")
    void testIsSupported() {
        assertTrue(fileParseService.isSupported("test.pdf"));
        assertTrue(fileParseService.isSupported("test.doc"));
        assertTrue(fileParseService.isSupported("test.docx"));
        assertTrue(fileParseService.isSupported("test.txt"));
        assertTrue(fileParseService.isSupported("test.html"));
        assertTrue(fileParseService.isSupported("test.htm"));
        assertTrue(fileParseService.isSupported("test.png"));
        assertTrue(fileParseService.isSupported("test.jpeg"));
        assertFalse(fileParseService.isSupported("test.xyz"));
    }

    @Test
    @DisplayName("获取支持的文件类型列表")
    void testGetSupportedTypes() {
        var types = fileParseService.getSupportedTypes();

        assertTrue(types.contains("pdf"));
        assertTrue(types.contains("doc"));
        assertTrue(types.contains("docx"));
        assertTrue(types.contains("txt"));
        assertTrue(types.contains("html"));
        assertTrue(types.contains("htm"));
    }

    @Test
    @DisplayName("解析 PNG 图片应保存 base64 和 MIME")
    void testParsePngImage() {
        byte[] fileData = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A};

        ParsedFile result = fileParseService.parse(fileData, "screen.png");

        assertTrue(result.isSuccess());
        assertEquals("image", result.getFileKind());
        assertEquals("image/png", result.getMimeType());
        assertNotNull(result.getBase64Data());
        assertNull(result.getContent());
    }

    @Test
    @DisplayName("解析空文件返回空内容")
    void testParseEmptyFile() {
        byte[] emptyData = new byte[0];

        ParsedFile result = fileParseService.parse(emptyData, "empty.txt");

        // 空文件可以解析成功，只是内容为空
        assertTrue(result.isSuccess());
        assertEquals("", result.getContent());
    }

    @Test
    @DisplayName("文件名无扩展名时返回空类型")
    void testFileNameWithoutExtension() {
        byte[] fileData = "test".getBytes(StandardCharsets.UTF_8);

        ParsedFile result = fileParseService.parse(fileData, "noextension");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("不支持的文件类型"));
    }
}
