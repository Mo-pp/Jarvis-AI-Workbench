package com.msz.resume.ai.resume.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 响应解析工具
 *
 * <p>从 LLM 返回的内容中提取 JSON，处理各种格式情况。
 *
 * <h2>处理的格式情况</h2>
 * <ul>
 *   <li>纯 JSON 字符串</li>
 *   <li>Markdown 代码块包裹的 JSON（```json ... ```）</li>
 *   <li>前后有额外文字的 JSON</li>
 *   <li>多个 JSON 对象（取第一个完整的）</li>
 * </ul>
 */
@Slf4j
public final class ResumeJsonParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 匹配 JSON 对象的正则
     * 从 { 开始，到对应的 } 结束
     */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    /**
     * 匹配 Markdown 代码块中的 JSON
     */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```");

    private ResumeJsonParser() {
        // 工具类不允许实例化
    }

    /**
     * 从 LLM 响应中解析指定类型的对象
     *
     * @param content      LLM 返回的内容
     * @param targetClass  目标类型
     * @param <T>          泛型
     * @return 解析后的对象，解析失败返回 null
     */
    public static <T> T parse(String content, Class<T> targetClass) {
        if (content == null || content.isBlank()) {
            log.warn("[ResumeJsonParser] 内容为空");
            return null;
        }

        // 1. 尝试直接解析
        T result = tryParseDirect(content, targetClass);
        if (result != null) {
            return result;
        }

        // 2. 尝试从代码块中提取
        result = tryParseFromCodeBlock(content, targetClass);
        if (result != null) {
            return result;
        }

        // 3. 尝试提取第一个 JSON 对象
        result = tryParseFromJsonObject(content, targetClass);
        if (result != null) {
            return result;
        }

        log.warn("[ResumeJsonParser] 无法从内容中解析 JSON，内容前200字符: {}",
                content.length() > 200 ? content.substring(0, 200) : content);
        return null;
    }

    /**
     * 直接解析 JSON
     */
    private static <T> T tryParseDirect(String content, Class<T> targetClass) {
        try {
            return OBJECT_MAPPER.readValue(content.trim(), targetClass);
        } catch (JsonProcessingException e) {
            log.debug("[ResumeJsonParser] 直接解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Markdown 代码块中提取并解析
     */
    private static <T> T tryParseFromCodeBlock(String content, Class<T> targetClass) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        if (matcher.find()) {
            String jsonContent = matcher.group(1).trim();
            log.debug("[ResumeJsonParser] 从代码块中提取到 JSON，长度: {}", jsonContent.length());
            return tryParseDirect(jsonContent, targetClass);
        }
        return null;
    }

    /**
     * 从内容中提取第一个 JSON 对象并解析
     */
    private static <T> T tryParseFromJsonObject(String content, Class<T> targetClass) {
        // 找到第一个 { 的位置
        int startIndex = content.indexOf('{');
        if (startIndex == -1) {
            return null;
        }

        // 从第一个 { 开始，找到匹配的 }
        int depth = 0;
        int endIndex = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIndex; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        endIndex = i + 1;
                        break;
                    }
                }
            }
        }

        if (endIndex == -1) {
            return null;
        }

        String jsonContent = content.substring(startIndex, endIndex);
        log.debug("[ResumeJsonParser] 提取到 JSON 对象，长度: {}", jsonContent.length());
        return tryParseDirect(jsonContent, targetClass);
    }

    /**
     * 将对象序列化为 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串，失败返回 null
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[ResumeJsonParser] 序列化失败", e);
            return null;
        }
    }

    /**
     * 美化 JSON 输出
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[ResumeJsonParser] 序列化失败", e);
            return null;
        }
    }
}
