package com.msz.resume.ai.chat.api.dto;

import lombok.Data;

/**
 * 会话置顶请求 DTO。
 */
@Data
public class SessionPinRequest {
    /** 是否置顶 */
    private Boolean pinned;
}
