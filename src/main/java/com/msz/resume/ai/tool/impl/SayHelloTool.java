package com.msz.resume.ai.tool.impl;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 问候工具（延迟工具）
 *
 * 延迟工具首轮只显示名称，需要时通过 toolSearch 加载完整 schema
 */
@Slf4j
@Component
public class SayHelloTool {

    /**
     * 生成问候语
     *
     * @param name 用户名
     * @return 问候语
     */
    @Tool("生成问候语，参数：name=用户名")
    public String sayHello(String name) {
        log.info("[工具调用] sayHello, name={}", name);
        if (name == null || name.isBlank()) {
            name = "朋友";
        }
        return "你好，" + name + "！很高兴认识你！";
    }
}
