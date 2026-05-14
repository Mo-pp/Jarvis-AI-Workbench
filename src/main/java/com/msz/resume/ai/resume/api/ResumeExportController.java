package com.msz.resume.ai.resume.api;

import com.msz.resume.ai.resume.export.dto.ExportPdfRequest;
import com.msz.resume.ai.resume.export.service.ResumePdfExportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/resume/export")
public class ResumeExportController {

    private final ResumePdfExportService resumePdfExportService;

    public ResumeExportController(ResumePdfExportService resumePdfExportService) {
        this.resumePdfExportService = resumePdfExportService;
    }

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody ExportPdfRequest request) {
        log.info("[ResumeExportController] 收到 PDF 导出请求: fileName={}, htmlLength={}",
                request.getFileName(), request.getHtml() != null ? request.getHtml().length() : 0);

        byte[] pdfBytes = resumePdfExportService.export(request.getHtml());
        String fileName = normalizeFileName(request.getFileName());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }

    private String normalizeFileName(String fileName) {
        String normalized = fileName == null ? "resume.pdf" : fileName.trim();
        if (normalized.isBlank()) {
            normalized = "resume.pdf";
        }
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!normalized.toLowerCase().endsWith(".pdf")) {
            normalized += ".pdf";
        }
        return normalized;
    }
}
