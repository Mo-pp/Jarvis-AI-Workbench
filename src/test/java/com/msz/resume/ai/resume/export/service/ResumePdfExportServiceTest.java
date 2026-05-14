package com.msz.resume.ai.resume.export.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumePdfExportServiceTest {

    private final ResumePdfExportService service = new ResumePdfExportService();

    @Test
    @DisplayName("HTML 可以导出为非空 PDF 字节流")
    void shouldExportPdfFromHtml() {
        String html = """
                <html>
                  <head><title>Resume</title></head>
                  <body>
                    <div class="resume-page-shell">
                      <div class="resume-paper">
                        <h1>张三</h1>
                        <p>五年后端开发经验，熟悉 Java、Spring Boot、MySQL、Redis。</p>
                      </div>
                    </div>
                  </body>
                </html>
                """;

        byte[] pdfBytes = service.export(html);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    @DisplayName("空 HTML 会被拒绝导出")
    void shouldRejectBlankHtml() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.export(" ")
        );

        assertTrue(exception.getMessage().contains("html"));
    }
}
