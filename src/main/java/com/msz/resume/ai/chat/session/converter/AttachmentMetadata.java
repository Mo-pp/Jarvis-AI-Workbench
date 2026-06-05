package com.msz.resume.ai.chat.session.converter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fileId;
    private String fileName;
    private String fileType;
    private String fileKind;
    private String mimeType;
    private Long fileSize;
    private boolean available;
}
