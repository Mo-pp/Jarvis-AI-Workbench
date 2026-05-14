package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * OpenViking content/read 响应体。
 *
 * <p>成功时通常返回：</p>
 * <ul>
 *     <li>status=ok</li>
 *     <li>result=文件内容（通常是字符串）</li>
 * </ul>
 *
 * <p>失败时可能返回 HTTP 错误，也可能返回 HTTP 200 但 body 中 status=error。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenVikingReadResponse(
        String status,
        Object result,
        ErrorInfo error,
        Double time
) {

    /**
     * 读取失败时的业务错误信息。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(
            String code,
            String message,
            Map<String, Object> details
    ) {
    }
}
