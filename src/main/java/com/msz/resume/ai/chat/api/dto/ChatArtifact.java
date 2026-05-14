package com.msz.resume.ai.chat.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端工作台可识别的统一产物信封。
 *
 * <p>Controller 层统一把工具结果或显式 artifact 响应归一化为该结构，
 * 前端只根据 type 分发到对应组件，避免在普通聊天文本里猜测 JSON 类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatArtifact {

    /**
     * 产物类型，如 mindmap/questionnaire/resume/optimize_result/markdown。
     */
    private String type;

    /**
     * 产物内容。通常是一个 JSON object，会按 type 交给前端组件归一化。
     */
    private Object payload;

    /**
     * 产物来源，如 tool:generateMindmap、assistant。
     */
    private String source;
}
