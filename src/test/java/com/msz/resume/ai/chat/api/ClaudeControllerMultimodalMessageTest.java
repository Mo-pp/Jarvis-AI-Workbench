package com.msz.resume.ai.chat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.chat.api.dto.ChatRequest;
import com.msz.resume.ai.file.dto.ParsedFile;
import com.msz.resume.ai.file.service.FileStorageService;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeControllerMultimodalMessageTest {

    private FileStorageService fileStorageService;
    private ClaudeController controller;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        controller = new ClaudeController(
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                fileStorageService,
                null,
                null,
                null,
                null,
                600000L
        );
    }

    @Test
    @DisplayName("文本和图片附件应组装为 TextContent + ImageContent")
    void buildUserMessageCreatesTextAndImageContent() {
        when(fileStorageService.get("img-1")).thenReturn(Optional.of(image("img-1", "screen.png", "aGVsbG8=")));

        ChatRequest request = new ChatRequest();
        request.setUserMessage("描述这张图");
        request.setImageFileIds(List.of("img-1"));

        UserMessage userMessage = controller.buildUserMessage(request);
        List<Content> contents = userMessage.contents();

        assertEquals(2, contents.size());
        assertInstanceOf(TextContent.class, contents.get(0));
        assertInstanceOf(ImageContent.class, contents.get(1));
        assertEquals("描述这张图", ((TextContent) contents.get(0)).text());
        ImageContent imageContent = (ImageContent) contents.get(1);
        assertEquals("aGVsbG8=", imageContent.image().base64Data());
        assertEquals("image/png", imageContent.image().mimeType());
    }

    @Test
    @DisplayName("文档附件进入文本，图片附件进入 ImageContent")
    void buildUserMessageCombinesDocumentTextAndImageContent() {
        when(fileStorageService.get("doc-1")).thenReturn(Optional.of(document("doc-1", "resume.txt", "简历正文")));
        when(fileStorageService.get("img-1")).thenReturn(Optional.of(image("img-1", "screen.png", "aGVsbG8=")));

        ChatRequest request = new ChatRequest();
        request.setUserMessage("结合文件和截图分析");
        request.setFileId("doc-1");
        request.setImageFileIds(List.of("img-1"));

        UserMessage userMessage = controller.buildUserMessage(request);

        assertEquals(2, userMessage.contents().size());
        String text = ((TextContent) userMessage.contents().get(0)).text();
        assertTrue(text.contains("结合文件和截图分析"));
        assertTrue(text.contains("- fileId: doc-1"));
        assertTrue(text.contains("- fileName: resume.txt"));
        assertTrue(text.contains("作为 sourceFileId 传入"));
        assertTrue(text.contains("文件内容（resume.txt）"));
        assertTrue(text.contains("简历正文"));
        assertInstanceOf(ImageContent.class, userMessage.contents().get(1));
    }

    @Test
    @DisplayName("图片过期时应降级为文本提示")
    void buildUserMessageFallsBackWhenImageExpired() {
        when(fileStorageService.get("expired-img")).thenReturn(Optional.empty());

        ChatRequest request = new ChatRequest();
        request.setUserMessage("看一下图片");
        request.setAttachmentIds(List.of("expired-img"));

        UserMessage userMessage = controller.buildUserMessage(request);

        assertEquals(1, userMessage.contents().size());
        String text = ((TextContent) userMessage.contents().get(0)).text();
        assertTrue(text.contains("看一下图片"));
        assertTrue(text.contains("[图片已过期或不存在：expired-img]"));
    }

    private ParsedFile image(String fileId, String fileName, String base64Data) {
        return ParsedFile.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileType("png")
                .fileKind("image")
                .mimeType("image/png")
                .fileSize(5L)
                .base64Data(base64Data)
                .parsedAt(Instant.now())
                .success(true)
                .build();
    }

    private ParsedFile document(String fileId, String fileName, String content) {
        return ParsedFile.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileType("txt")
                .fileKind("document")
                .fileSize(content.length())
                .content(content)
                .parsedAt(Instant.now())
                .success(true)
                .build();
    }
}
