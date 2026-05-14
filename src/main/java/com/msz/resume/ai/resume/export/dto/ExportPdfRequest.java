package com.msz.resume.ai.resume.export.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportPdfRequest {

    @NotBlank(message = "html 不能为空")
    private String html;

    @NotBlank(message = "fileName 不能为空")
    private String fileName;
}
