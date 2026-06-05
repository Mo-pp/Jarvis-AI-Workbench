package com.msz.resume.ai.file.api;

import com.msz.resume.ai.file.dto.FileUploadResponse;
import com.msz.resume.ai.file.dto.ParsedFile;
import com.msz.resume.ai.file.service.FileParseService;
import com.msz.resume.ai.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传控制器
 *
 * <p>处理文件上传请求，解析后存储在 Redis 中，供对话时引用。
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final long EXPIRES_IN_SECONDS = 15 * 60; // 15 分钟
    private static final int IMAGE_PREVIEW_BASE64_LIMIT = 256 * 1024;

    private final FileParseService fileParseService;
    private final FileStorageService fileStorageService;

    /**
     * 上传文件
     *
     * <p>接收文件后立即解析，返回文件ID供后续对话引用。
     *
     * @param file 上传的文件
     * @return 上传响应
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("[FileController] 收到文件上传请求: {}, 大小: {} bytes",
                fileName, file.getSize());

        // 检查文件是否为空
        if (file.isEmpty()) {
            log.warn("[FileController] 文件为空");
            return ResponseEntity.badRequest()
                    .body(FileUploadResponse.builder()
                            .success(false)
                            .errorMessage("文件不能为空")
                            .build());
        }

        // 检查文件名
        if (fileName == null || fileName.isBlank()) {
            log.warn("[FileController] 文件名为空");
            return ResponseEntity.badRequest()
                    .body(FileUploadResponse.builder()
                            .success(false)
                            .errorMessage("文件名不能为空")
                            .build());
        }

        // 检查文件类型
        if (!fileParseService.isSupported(fileName)) {
            log.warn("[FileController] 不支持的文件类型: {}", fileName);
            return ResponseEntity.badRequest()
                            .body(FileUploadResponse.builder()
                                    .fileName(fileName)
                                    .success(false)
                                    .errorMessage("不支持的文件类型。支持：PDF、Word (.doc/.docx)、TXT、HTML、PNG、JPEG、WEBP、GIF")
                                    .build());
        }

        try {
            // 读取文件内容
            byte[] fileData = file.getBytes();

            // 解析文件
            ParsedFile parsedFile = fileParseService.parse(fileData, fileName);

            // 存储到 Redis
            if (parsedFile.isSuccess()) {
                fileStorageService.save(parsedFile);
            }

            // 构建响应
            FileUploadResponse response = toResponse(parsedFile);

            log.info("[FileController] 文件上传完成: fileId={}, success={}",
                    parsedFile.getFileId(), parsedFile.isSuccess());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("[FileController] 读取文件失败: {}", fileName, e);
            return ResponseEntity.internalServerError()
                    .body(FileUploadResponse.builder()
                            .fileName(fileName)
                            .success(false)
                            .errorMessage("读取文件失败：" + e.getMessage())
                            .build());
        }
    }

    /**
     * 获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<FileUploadResponse> getFileInfo(@PathVariable String fileId) {
        return fileStorageService.get(fileId)
                .map(parsedFile -> ResponseEntity.ok(toResponse(parsedFile)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除文件
     *
     * @param fileId 文件ID
     * @return 删除结果
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        fileStorageService.delete(fileId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 转换为响应对象
     */
    private FileUploadResponse toResponse(ParsedFile parsedFile) {
        String contentPreview = null;
        if (parsedFile.getContent() != null && !parsedFile.getContent().isEmpty()) {
            int previewLength = Math.min(200, parsedFile.getContent().length());
            contentPreview = parsedFile.getContent().substring(0, previewLength);
            if (parsedFile.getContent().length() > 200) {
                contentPreview += "...";
            }
        }

        return FileUploadResponse.builder()
                .fileId(parsedFile.getFileId())
                .fileName(parsedFile.getFileName())
                .fileType(parsedFile.getFileType())
                .fileKind(parsedFile.getFileKind())
                .mimeType(parsedFile.getMimeType())
                .fileSize(parsedFile.getFileSize())
                .contentPreview(contentPreview)
                .previewUrl(imagePreviewUrl(parsedFile))
                .success(parsedFile.isSuccess())
                .errorMessage(parsedFile.getErrorMessage())
                .expiresInSeconds(EXPIRES_IN_SECONDS)
                .build();
    }

    private String imagePreviewUrl(ParsedFile parsedFile) {
        if (!"image".equals(parsedFile.getFileKind())
                || parsedFile.getBase64Data() == null
                || parsedFile.getMimeType() == null
                || parsedFile.getBase64Data().length() > IMAGE_PREVIEW_BASE64_LIMIT) {
            return null;
        }
        return "data:" + parsedFile.getMimeType() + ";base64," + parsedFile.getBase64Data();
    }
}
