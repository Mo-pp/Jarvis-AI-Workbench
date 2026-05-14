package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenViking content/write 响应体。
 *
 * <p>OpenViking 在写入成功时会返回 status=ok 和 result；
 * 失败时可能返回 HTTP 错误，也可能返回 HTTP 200 但 body 中 status=error。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingWriteResponse(
        String status,
        Result result,
        ErrorInfo error,
        Double time
) {

    /**
     * 写入成功后的结果信息。
     *
     * <p>字段名采用宽松映射，兼容不同版本 OpenViking 可能返回的结果结构。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String uri,
            String mode,
            String status,
            @JsonProperty("task_id") String taskId,
            String message
    ) {
    }

    /**
     * 写入失败时的业务错误信息。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(
            String code,
            String message,
            Map<String, Object> details
    ) {
    }
}
