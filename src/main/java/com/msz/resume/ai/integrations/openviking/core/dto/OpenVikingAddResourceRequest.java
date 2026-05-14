package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenViking add_resource 请求。
 *
 * @param path          远程资源源URL（与temp_file_id二选一）
 * @param tempFileId    临时上传文件ID（与path二选一）
 * @param to            目标URI
 * @param parent        父目录URI（与to互斥）
 * @param reason        添加原因
 * @param instruction   处理指令
 * @param wait          是否等待处理完成
 * @param timeout       超时秒数
 * @param strict        是否严格模式
 * @param sourceName    源名称
 * @param ignoreDirs    忽略目录（逗号分隔）
 * @param include       包含模式
 * @param exclude       排除模式
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingAddResourceRequest(
        @JsonProperty("path")
        String path,

        @JsonProperty("temp_file_id")
        String tempFileId,

        @JsonProperty("to")
        String to,

        @JsonProperty("parent")
        String parent,

        @JsonProperty("reason")
        String reason,

        @JsonProperty("instruction")
        String instruction,

        @JsonProperty("wait")
        Boolean waitForProcessing,

        @JsonProperty("timeout")
        Float timeout,

        @JsonProperty("strict")
        Boolean strict,

        @JsonProperty("source_name")
        String sourceName,

        @JsonProperty("ignore_dirs")
        String ignoreDirs,

        @JsonProperty("include")
        String include,

        @JsonProperty("exclude")
        String exclude
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String path;
        private String tempFileId;
        private String to;
        private String parent;
        private String reason = "";
        private String instruction = "";
        private Boolean waitForProcessing = false;
        private Float timeout;
        private Boolean strict = false;
        private String sourceName;
        private String ignoreDirs;
        private String include;
        private String exclude;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder tempFileId(String tempFileId) {
            this.tempFileId = tempFileId;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder parent(String parent) {
            this.parent = parent;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        public Builder wait(Boolean waitForProcessing) {
            this.waitForProcessing = waitForProcessing;
            return this;
        }

        public Builder timeout(Float timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public Builder sourceName(String sourceName) {
            this.sourceName = sourceName;
            return this;
        }

        public Builder ignoreDirs(String ignoreDirs) {
            this.ignoreDirs = ignoreDirs;
            return this;
        }

        public Builder include(String include) {
            this.include = include;
            return this;
        }

        public Builder exclude(String exclude) {
            this.exclude = exclude;
            return this;
        }

        public OpenVikingAddResourceRequest build() {
            return new OpenVikingAddResourceRequest(
                    path, tempFileId, to, parent, reason, instruction,
                    waitForProcessing, timeout, strict, sourceName, ignoreDirs, include, exclude
            );
        }
    }
}
