package com.msz.resume.ai.integrations.openviking.core.session;

import com.msz.resume.ai.integrations.openviking.core.dto.OpenVikingSessionContextResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * OpenViking Session Context 格式化器。
 *
 * <p>将 OpenViking getSessionContext 响应转换为受预算约束的 Markdown section，
 * 供 compact/recovery 场景注入 LLM 上下文。</p>
 */
@Component
public class OpenVikingSessionContextFormatter {

    private static final int APPROX_CHARS_PER_TOKEN = 4;
    private static final int DEFAULT_MAX_MESSAGE_CHARS = 200;

    @SuppressWarnings("unchecked")
    public String format(OpenVikingSessionContextResponse response, int tokenBudget) {
        if (response == null || response.result() == null) {
            return null;
        }

        int charBudget = Math.max(tokenBudget, 1) * APPROX_CHARS_PER_TOKEN;
        Budget budget = new Budget(charBudget);
        Map<String, Object> result = response.result();
        StringBuilder sb = new StringBuilder();

        appendSection(sb, budget, "## OpenViking Session Context\n\n");

        Object archiveOverview = result.get("latest_archive_overview");
        if (hasTextValue(archiveOverview)) {
            appendSection(sb, budget, "### Current State\n");
            appendSection(sb, budget, archiveOverview.toString().trim() + "\n\n");
        }

        Object abstracts = result.get("pre_archive_abstracts");
        if (abstracts instanceof List<?> abstractList && !abstractList.isEmpty()) {
            appendSection(sb, budget, "### Decisions Made\n");
            for (Object abs : abstractList) {
                if (!hasTextValue(abs)) {
                    continue;
                }
                if (!appendSection(sb, budget, "- " + abs.toString().trim() + "\n")) {
                    break;
                }
            }
            appendSection(sb, budget, "\n");
        }

        Object messages = result.get("messages");
        if (messages instanceof List<?> messageList && !messageList.isEmpty()) {
            appendSection(sb, budget, "### Recent Active Messages\n");
            for (Object msg : messageList) {
                if (!(msg instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> msgMap = (Map<String, Object>) rawMap;
                String role = String.valueOf(msgMap.getOrDefault("role", "unknown"));
                String content = extractTextFromParts(msgMap.get("parts"));
                if (!hasText(content)) {
                    continue;
                }
                String line = "- **" + role + "**: " + truncateText(content.trim(), DEFAULT_MAX_MESSAGE_CHARS) + "\n";
                if (!appendSection(sb, budget, line)) {
                    break;
                }
            }
            appendSection(sb, budget, "\n");
        }

        Object stats = result.get("stats");
        if (stats instanceof Map<?, ?> statsMap) {
            appendSection(sb, budget, "### Stats\n");
            appendSection(sb, budget, "- Total Archives: " + valueOrDefault(statsMap.get("totalArchives"), 0) + "\n");
            appendSection(sb, budget, "- Active Tokens: " + valueOrDefault(statsMap.get("activeTokens"), 0) + "\n");
            appendSection(sb, budget, "- Archive Tokens: " + valueOrDefault(statsMap.get("archiveTokens"), 0) + "\n\n");
        }

        appendSection(sb, budget, "### Next Step\n继续当前会话任务时，优先参考 Current State、Decisions Made 和 Recent Active Messages。\n");

        String formatted = sb.toString().trim();
        if (!hasText(formatted) || formatted.equals("## OpenViking Session Context")) {
            return null;
        }
        return formatted;
    }

    private boolean appendSection(StringBuilder sb, Budget budget, String content) {
        if (!hasText(content) || budget.remaining <= 0) {
            return false;
        }
        String value = content;
        if (value.length() > budget.remaining) {
            if (budget.remaining <= 20) {
                return false;
            }
            value = value.substring(0, budget.remaining - 3) + "...";
        }
        sb.append(value);
        budget.remaining -= value.length();
        return value.length() == content.length();
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromParts(Object parts) {
        if (parts == null) {
            return null;
        }
        if (parts instanceof String text) {
            return text;
        }
        if (parts instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object part : list) {
                if (part instanceof Map<?, ?> rawPart) {
                    Map<String, Object> partMap = (Map<String, Object>) rawPart;
                    Object text = partMap.get("text");
                    if (hasTextValue(text)) {
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
        return parts.toString();
    }

    private String truncateText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private Object valueOrDefault(Object value, Object defaultValue) {
        return value != null ? value : defaultValue;
    }

    private boolean hasTextValue(Object value) {
        return value != null && hasText(value.toString());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static class Budget {
        private int remaining;

        private Budget(int remaining) {
            this.remaining = remaining;
        }
    }
}
