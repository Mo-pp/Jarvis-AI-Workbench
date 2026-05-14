package com.msz.resume.ai.chat.api.dto;

import lombok.Data;

/**
 * 会话重命名请求 DTO。
 */
@Data
public class SessionRenameRequest {
    /** 新标题 */
    private String title;
}
