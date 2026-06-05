package com.msz.resume.ai.chat.runtime.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final int MIN_BASE64_CHARS = 128;
    private static final int MIN_RAW_BASE64_CHARS = 512;

    private static final Pattern LANGCHAIN_BASE64_FIELD = Pattern.compile(
            "(base64Data\\s*=\\s*\")([^\"]{" + MIN_BASE64_CHARS + ",})(\")",
            Pattern.DOTALL);
    private static final Pattern JSON_BASE64_FIELD = Pattern.compile(
            "(\"base64Data\"\\s*:\\s*\")([^\"]{" + MIN_BASE64_CHARS + ",})(\")",
            Pattern.DOTALL);
    private static final Pattern DATA_URL_BASE64 = Pattern.compile(
            "(data:image/[A-Za-z0-9.+-]+;base64,)([A-Za-z0-9+/=_-]{" + MIN_BASE64_CHARS + ",})");
    private static final Pattern RAW_BASE64_BLOCK = Pattern.compile(
            "(?<![A-Za-z0-9+/=_-])([A-Za-z0-9+/=_-]{" + MIN_RAW_BASE64_CHARS + ",})(?![A-Za-z0-9+/=_-])");

    private LogSanitizer() {
    }

    public static String sanitizeLargeInlineData(Object value) {
        if (value == null) {
            return "null";
        }
        return sanitizeLargeInlineData(String.valueOf(value));
    }

    public static String sanitizeLargeInlineData(String text) {
        if (text == null) {
            return null;
        }

        String sanitized = replaceCapturedValue(LANGCHAIN_BASE64_FIELD, text);
        sanitized = replaceCapturedValue(JSON_BASE64_FIELD, sanitized);
        sanitized = replaceCapturedValueWithoutSuffix(DATA_URL_BASE64, sanitized);
        return replaceRawBase64Blocks(sanitized);
    }

    private static String replaceCapturedValue(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1)
                    + placeholder(matcher.group(2).length())
                    + matcher.group(3);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceCapturedValueWithoutSuffix(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + placeholder(matcher.group(2).length());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceRawBase64Blocks(String text) {
        Matcher matcher = RAW_BASE64_BLOCK.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            if (hasBase64Signal(value)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(placeholder(value.length())));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean hasBase64Signal(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '+' || ch == '/' || ch == '=' || ch == '_' || ch == '-') {
                return true;
            }
        }
        return false;
    }

    private static String placeholder(int length) {
        return "[base64 omitted, chars=" + length + "]";
    }
}
