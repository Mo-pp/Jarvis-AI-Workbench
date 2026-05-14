package com.msz.resume.ai.chat.prompt.model;

import java.io.Serializable;
import java.util.Map;

/**
 * 用户上下文信息
 *
 * <p>用于动态section构建，注入用户身份和偏好信息。
 */
public record UserContext(
        String userId,
        String username,
        String role,
        String language,
        String outputStyle,
        Map<String, Object> businessContext
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 创建空上下文（使用默认值）
     */
    public static UserContext empty() {
        return new UserContext(null, null, null, "zh-CN", "concise", Map.of());
    }

    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent Builder
     */
    public static class Builder {
        private String userId;
        private String username;
        private String role;
        private String language = "zh-CN";
        private String outputStyle = "concise";
        private Map<String, Object> businessContext = Map.of();

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder outputStyle(String outputStyle) {
            this.outputStyle = outputStyle;
            return this;
        }

        public Builder businessContext(Map<String, Object> ctx) {
            this.businessContext = ctx;
            return this;
        }

        public UserContext build() {
            return new UserContext(userId, username, role, language, outputStyle, businessContext);
        }
    }
}
