package com.msz.resume.ai.integrations.openviking.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenViking content/write 请求体。
 *
 * <p>当前用于向 OpenViking 文件系统写入文本内容。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *     <li>uri：目标 Viking URI</li>
 *     <li>content：要写入的文本内容</li>
 *     <li>mode：写入模式，典型值为 create / replace / append</li>
 *     <li>waitForProcessing：是否等待服务端后处理完成后再返回，对应 OpenViking 的 wait 字段</li>
 *     <li>timeout：服务端处理等待超时时间（秒），可选</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenVikingWriteRequest(
        String uri,
        String content,
        String mode,
        @JsonProperty("wait") Boolean waitForProcessing,
        Double timeout
) {
}
