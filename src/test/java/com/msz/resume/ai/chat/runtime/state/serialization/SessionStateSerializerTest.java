package com.msz.resume.ai.chat.runtime.state.serialization;

import com.msz.resume.ai.chat.session.converter.AttachmentMetadata;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SessionStateSerializerTest {

    @Test
    @DisplayName("可序列化带图片附件 metadata 的用户消息")
    void serializesUserMessageWithAttachmentMetadata() {
        SessionStateSerializer serializer = new SessionStateSerializer();
        UserMessage message = UserMessage.builder()
                .contents(List.of(TextContent.from("看图")))
                .attributes(Map.of("attachments", List.of(
                        AttachmentMetadata.builder()
                                .fileId("img-1")
                                .fileName("screen.png")
                                .fileKind("image")
                                .mimeType("image/png")
                                .available(true)
                                .build()
                )))
                .build();

        assertDoesNotThrow(() -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                serializer.writeData(Map.of("messages", List.of(message)), out);
            }
        });
    }
}
