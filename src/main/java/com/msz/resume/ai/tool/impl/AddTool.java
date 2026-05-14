package com.msz.resume.ai.tool.impl;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 计算器工具（延迟工具）
 *
 * 延迟工具首轮只显示名称，需要时通过 toolSearch 加载完整 schema
 */
@Slf4j
@Component
public class AddTool {

    /**
     * 计算两数之和
     *
     * @param a 第一个数
     * @param b 第二个数
     * @return 计算结果
     */
    @Tool("计算两个数的和，参数：a=第一个数，b=第二个数")
    public String add(int a, int b) {
        log.info("[工具调用] add, a={}, b={}", a, b);
        int result = a + b;
        return String.format("%d + %d = %d", a, b, result);
    }
}
