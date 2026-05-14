package com.msz.resume.ai.tool.impl;

import com.msz.resume.ai.tool.CoreTool;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 获取当前时间工具（核心工具）
 *
 * 核心工具始终完整加载到 LLM 请求中，无需通过 toolSearch 发现
 */
@Slf4j
@CoreTool
@Component
public class GetCurrentTimeTool {

    /**
     * 获取当前时间
     *
     * @return 当前时间字符串，格式：yyyy-MM-dd HH:mm:ss
     */
    @Tool("获取当前日期和时间，返回格式：yyyy-MM-dd HH:mm:ss")
    public String getCurrentTime() {
        log.info("[工具调用] getCurrentTime");
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
