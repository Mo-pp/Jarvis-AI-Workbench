package com.msz.resume.ai.tool.impl;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 测试工具类
 *
 * 使用@Tool注解定义工具方法，LangChain4j会自动识别
 * 用于测试状态机的工具调用功能
 */
@Slf4j
@Component
public class HelloWorldTool {

    /**
     * 获取当前时间
     *
     * 大模型可以调用这个工具获取当前时间
     *
     * @return 当前时间字符串
     */
    @Tool("获取当前日期和时间，返回格式：yyyy-MM-dd HH:mm:ss")
    public String getCurrentTime() {
        log.info("[工具调用] getCurrentTime");
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 简单的问候工具
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

    /**
     * 计算器工具（简单版）
     *
     * @param a 第一个数
     * @param b 第二个数
     * @return 两数之和
     */
    @Tool("计算两个数的和，参数：a=第一个数，b=第二个数")
    public String add(int a, int b) {
        log.info("[工具调用] add, a={}, b={}", a, b);
        int result = a + b;
        return String.format("%d + %d = %d", a, b, result);
    }
}
