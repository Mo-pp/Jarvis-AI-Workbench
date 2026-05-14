package com.msz.resume.ai.chat.compression;

import com.msz.resume.ai.chat.compression.model.BudgetResult;
import com.msz.resume.ai.chat.session.service.ToolResultBlobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 默认工具结果预算裁剪器实现
 *
 * <p>实现规则：
 * <ul>
 *   <li>结果 ≤ maxResultSizeChars: 原样返回</li>
 *   <li>结果 > maxResultSizeChars: 持久化到数据库，返回预览</li>
 *   <li>预览截断：在最后一个完整换行处截断（50%之后）</li>
 *   <li>无换行文本：直接在预览大小处截断</li>
 * </ul>
 */
@Slf4j
@Component
public class DefaultToolResultBudget implements ToolResultBudget {

    private final JarvisCompressionProperties properties;
    private final ToolResultBlobService blobService;

    public DefaultToolResultBudget(JarvisCompressionProperties properties, ToolResultBlobService blobService) {
        this.properties = properties;
        this.blobService = blobService;
    }

    @Override
    public BudgetResult process(String toolName, String toolCallId, String result, String sessionId) {
        if (result == null || result.isEmpty()) {
            return BudgetResult.unchanged("");
        }

        int originalSize = result.length();

        // 检查是否需要裁剪
        if (!needsTruncation(originalSize)) {
            return BudgetResult.unchanged(result);
        }

        // 生成预览
        String preview = generatePreview(result);

        // 持久化完整内容到数据库
        Long blobId = blobService.save(sessionId, toolName, toolCallId, result, originalSize);
        String persistedRef = "blob:" + blobId;

        // 返回截断结果
        BudgetResult budgetResult = BudgetResult.truncated(
                buildPreviewMessage(preview, blobId, originalSize),
                persistedRef,
                originalSize
        );

        log.info("[ToolResultBudget] 工具 {} 结果已截断，原始大小: {} 字符，存储ID: {}",
                toolName, originalSize, blobId);

        return budgetResult;
    }

    @Override
    public boolean needsTruncation(int resultSize) {
        return resultSize > properties.getMaxResultSizeChars();
    }

    /**
     * 生成预览内容
     *
     * <p>截断策略：
     * <ol>
     *   <li>取前 previewSizeBytes 字节</li>
     *   <li>在 50% 位置之后寻找最后一个换行符</li>
     *   <li>有换行符：在该处截断</li>
     *   <li>无换行符：直接在 previewSizeBytes 处截断</li>
     * </ol>
     *
     * @param content 原始内容
     * @return 预览内容
     */
    private String generatePreview(String content) {
        int previewBytes = properties.getPreviewSizeBytes();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        // 如果内容本身就小于预览大小，直接返回
        if (bytes.length <= previewBytes) {
            return content;
        }

        // 截取前 previewBytes 字节
        String preview = new String(bytes, 0, previewBytes, StandardCharsets.UTF_8);

        // 在 50% 位置之后寻找最后一个换行符
        int halfLength = preview.length() / 2;
        int lastNewline = preview.lastIndexOf('\n', preview.length() - 1);

        // 从后往前找，但必须在 50% 位置之后
        while (lastNewline > halfLength) {
            // 找到了，在该处截断
            return preview.substring(0, lastNewline + 1);
        }

        // 如果在 50% 之后没找到换行符，尝试在整个预览中找
        lastNewline = preview.lastIndexOf('\n');
        if (lastNewline > 0) {
            return preview.substring(0, lastNewline + 1);
        }

        // 完全没有换行符，直接截断
        // 注意：直接截断可能导致多字节字符被截断，需要处理
        return safeTruncate(bytes, previewBytes);
    }

    /**
     * 安全截断字节数组，避免截断多字节字符
     */
    private String safeTruncate(byte[] bytes, int maxBytes) {
        // 从 maxBytes 位置往前找，直到遇到有效的 UTF-8 字符边界
        int truncateAt = maxBytes;
        while (truncateAt > 0) {
            // 检查当前字节是否是 UTF-8 序列的起始字节
            byte b = bytes[truncateAt - 1];
            // UTF-8 起始字节：0xxxxxxx (ASCII) 或 11xxxxxx (多字节起始)
            // UTF-8 续字节：10xxxxxx
            if ((b & 0xC0) != 0x80) {
                // 找到起始字节
                break;
            }
            truncateAt--;
        }

        return new String(bytes, 0, truncateAt, StandardCharsets.UTF_8);
    }

    /**
     * 构建预览消息（包含提示信息）
     */
    private String buildPreviewMessage(String preview, Long blobId, int originalSize) {
        StringBuilder sb = new StringBuilder();

        sb.append("<persisted-output>\n");
        sb.append(String.format("输出过大 (%d 字符)。完整内容已保存到数据库，ID: %d\n\n",
                originalSize, blobId));
        sb.append("预览 (前 ").append(properties.getPreviewSizeBytes()).append(" 字节):\n");
        sb.append(preview);

        // 如果预览没有以换行结尾，添加省略号
        if (!preview.endsWith("\n")) {
            sb.append("...");
        }

        sb.append("\n</persisted-output>");

        return sb.toString();
    }
}
