package com.msz.resume.ai.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 核心工具标记注解
 *
 * 标注此注解的工具类将被视为核心工具，始终完整加载到 LLM 请求中。
 * 未标注此注解的工具类将被视为延迟工具，仅在需要时通过 toolSearch 加载。
 *
 * 使用示例：
 * <pre>
 * {@literal @}CoreTool
 * {@literal @}Component
 * public class GetCurrentTimeTool {
 *     {@literal @}Tool("获取当前日期和时间")
 *     public String getCurrentTime() { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoreTool {
}
