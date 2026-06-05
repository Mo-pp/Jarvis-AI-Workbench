package com.msz.resume.ai.file.service;

import com.msz.resume.ai.file.dto.ParsedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件解析服务
 *
 * <p>支持解析 PDF、Word、TXT、HTML 格式文件，提取纯文本内容。
 */
@Slf4j
@Service
public class FileParseService {

    /**
     * 支持的文件类型
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "pdf", "doc", "docx", "txt", "html", "htm"
    );

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "png", "jpg", "jpeg", "webp", "gif"
    );

    private static final Map<String, String> IMAGE_MIME_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif"
    );

    /**
     * 最大文件大小：15MB
     */
    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024;

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;

    /**
     * 解析文件内容
     *
     * @param fileData  文件二进制数据
     * @param fileName  原始文件名
     * @return 解析结果
     */
    public ParsedFile parse(byte[] fileData, String fileName) {
        String fileId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String fileType = getFileExtension(fileName);

        if (SUPPORTED_IMAGE_TYPES.contains(fileType.toLowerCase(Locale.ROOT))) {
            return parseImage(fileData, fileName, fileId, fileType);
        }

        // 检查文件大小
        if (fileData.length > MAX_FILE_SIZE) {
            log.warn("[FileParseService] 文件过大: {} bytes, 文件名: {}", fileData.length, fileName);
            return ParsedFile.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileKind("document")
                    .fileSize(fileData.length)
                    .parsedAt(Instant.now())
                    .success(false)
                    .errorMessage("文件大小超过限制（最大 15MB）")
                    .build();
        }

        // 检查文件类型
        if (!SUPPORTED_TYPES.contains(fileType.toLowerCase())) {
            log.warn("[FileParseService] 不支持的文件类型: {}", fileType);
            return ParsedFile.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileKind("document")
                    .fileSize(fileData.length)
                    .parsedAt(Instant.now())
                    .success(false)
                    .errorMessage("不支持的文件类型：" + fileType + "。支持的类型：PDF、Word、TXT、HTML")
                    .build();
        }

        // 解析文件内容
        try {
            String content = doParse(fileData, fileName, fileType);
            log.info("[FileParseService] 文件解析成功: {}, 大小: {} bytes, 内容长度: {} 字符",
                    fileName, fileData.length, content.length());

            return ParsedFile.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileKind("document")
                    .fileSize(fileData.length)
                    .content(content)
                    .parsedAt(Instant.now())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("[FileParseService] 文件解析失败: {}", fileName, e);
            return ParsedFile.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileKind("document")
                    .fileSize(fileData.length)
                    .parsedAt(Instant.now())
                    .success(false)
                    .errorMessage("文件解析失败：" + e.getMessage())
                    .build();
        }
    }

    private ParsedFile parseImage(byte[] fileData, String fileName, String fileId, String fileType) {
        if (fileData.length > MAX_IMAGE_SIZE) {
            log.warn("[FileParseService] 图片过大: {} bytes, 文件名: {}", fileData.length, fileName);
            return ParsedFile.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileKind("image")
                    .mimeType(IMAGE_MIME_TYPES.get(fileType.toLowerCase(Locale.ROOT)))
                    .fileSize(fileData.length)
                    .parsedAt(Instant.now())
                    .success(false)
                    .errorMessage("图片大小超过限制（最大 10MB）")
                    .build();
        }

        return ParsedFile.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileType(fileType)
                .fileKind("image")
                .mimeType(IMAGE_MIME_TYPES.get(fileType.toLowerCase(Locale.ROOT)))
                .fileSize(fileData.length)
                .base64Data(Base64.getEncoder().encodeToString(fileData))
                .parsedAt(Instant.now())
                .success(true)
                .build();
    }

    /**
     * 执行解析
     */
    private String doParse(byte[] fileData, String fileName, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> parsePdf(fileData);
            case "doc" -> parseDoc(fileData);
            case "docx" -> parseDocx(fileData);
            case "txt" -> parseTxt(fileData);
            case "html", "htm" -> parseHtml(fileData);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    /**
     * 解析 PDF 文件
     */
    private String parsePdf(byte[] fileData) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileData))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document).trim();
        }
    }

    /**
     * 解析 .doc 文件（旧版 Word）
     */
    private String parseDoc(byte[] fileData) throws IOException {
        try (InputStream is = new ByteArrayInputStream(fileData);
             HWPFDocument document = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText().trim();
        }
    }

    /**
     * 解析 .docx 文件（新版 Word）
     */
    private String parseDocx(byte[] fileData) throws IOException {
        try (InputStream is = new ByteArrayInputStream(fileData);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().trim();
        }
    }

    /**
     * 解析纯文本文件
     */
    private String parseTxt(byte[] fileData) throws IOException {
        // 尝试检测编码，默认 UTF-8
        String content = tryDecode(fileData, StandardCharsets.UTF_8);
        if (content == null) {
            content = tryDecode(fileData, Charset.forName("GBK"));
        }
        if (content == null) {
            content = tryDecode(fileData, Charset.forName("GB2312"));
        }
        if (content == null) {
            content = new String(fileData, StandardCharsets.UTF_8);
        }
        return content.trim();
    }

    /**
     * 解析 HTML 文件
     */
    private String parseHtml(byte[] fileData) throws IOException {
        String html = new String(fileData, StandardCharsets.UTF_8);
        // 使用 Jsoup 清理 HTML 标签，只保留文本
        String text = Jsoup.clean(html, Safelist.none());
        return text.trim();
    }

    /**
     * 尝试解码文本
     */
    private String tryDecode(byte[] data, Charset charset) {
        try {
            String text = new String(data, charset);
            // 检查是否有乱码（替换字符）
            if (text.contains("�")) {
                return null;
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 检查文件类型是否支持
     */
    public boolean isSupported(String fileName) {
        String ext = getFileExtension(fileName);
        String normalized = ext.toLowerCase(Locale.ROOT);
        return SUPPORTED_TYPES.contains(normalized) || SUPPORTED_IMAGE_TYPES.contains(normalized);
    }

    /**
     * 获取支持的文件类型列表
     */
    public Set<String> getSupportedTypes() {
        Set<String> types = new LinkedHashSet<>(SUPPORTED_TYPES);
        types.addAll(SUPPORTED_IMAGE_TYPES);
        return types;
    }

    public boolean isImage(String fileName) {
        String ext = getFileExtension(fileName);
        return SUPPORTED_IMAGE_TYPES.contains(ext.toLowerCase(Locale.ROOT));
    }
}
