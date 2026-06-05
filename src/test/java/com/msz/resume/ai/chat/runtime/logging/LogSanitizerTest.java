package com.msz.resume.ai.chat.runtime.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {

    @Test
    @DisplayName("replaces LangChain4j base64Data field with placeholder")
    void sanitizesLangchainBase64Field() {
        String base64 = longBase64();
        String log = "Image { base64Data = \"" + base64 + "\", mimeType = \"image/png\" }";

        String sanitized = LogSanitizer.sanitizeLargeInlineData(log);

        assertFalse(sanitized.contains(base64));
        assertTrue(sanitized.contains("base64Data = \"[base64 omitted, chars="));
        assertTrue(sanitized.contains("mimeType = \"image/png\""));
    }

    @Test
    @DisplayName("replaces JSON base64Data and data URL values")
    void sanitizesJsonAndDataUrlValues() {
        String base64 = longBase64();
        String log = "{\"base64Data\":\"" + base64 + "\",\"url\":\"data:image/png;base64," + base64 + "\"}";

        String sanitized = LogSanitizer.sanitizeLargeInlineData(log);

        assertFalse(sanitized.contains(base64));
        assertTrue(sanitized.contains("\"base64Data\":\"[base64 omitted, chars="));
        assertTrue(sanitized.contains("data:image/png;base64,[base64 omitted, chars="));
    }

    @Test
    @DisplayName("replaces raw long base64 blocks")
    void sanitizesRawBase64Blocks() {
        String base64 = longBase64();

        String sanitized = LogSanitizer.sanitizeLargeInlineData("prefix " + base64 + " suffix");

        assertFalse(sanitized.contains(base64));
        assertTrue(sanitized.contains("[base64 omitted, chars="));
    }

    private static String longBase64() {
        byte[] bytes = new byte[1024];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}
