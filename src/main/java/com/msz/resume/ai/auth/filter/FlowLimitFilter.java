/**
 * 流量限制过滤器
 *
 * 作用：基于 IP 的请求频率限制，防止恶意请求
 *
 * 限制规则：
 * - 同一 IP 在 3 秒内最多 200 次请求
 * - 超限后封禁 30 秒
 * - OPTIONS 请求（CORS 预检）不限流
 *
 * 执行顺序：Order=-101，在 JwtAuthenticationFilter 之前执行
 */
package com.msz.resume.ai.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.auth.Const;
import com.msz.resume.ai.auth.common.RestBean;
import com.msz.resume.ai.auth.util.FlowUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;

@Component
@Order(-101)
public class FlowLimitFilter extends HttpFilter {

    /** 单周期内最大请求数 */
    private static final int LIMIT = 200;
    /** 统计周期（秒） */
    private static final int PERIOD = 3;
    /** 封禁时间（秒） */
    private static final int BLOCK_TIME = 30;

    private final FlowUtils flowUtils;
    private final ObjectMapper objectMapper;

    public FlowLimitFilter(FlowUtils flowUtils, ObjectMapper objectMapper) {
        this.flowUtils = flowUtils;
        this.objectMapper = objectMapper;
    }

    /**
     * 过滤器核心逻辑
     * 1. 检查 IP 是否被封禁
     * 2. 未封禁则检查请求频率
     * 3. 超限则封禁并返回 429
     */
    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String address = resolveClientAddress(request);
        String blockKey = Const.FLOW_LIMIT_BLOCK + address;

        // 1. 检查是否已被封禁
        if (flowUtils.isBlocked(blockKey)) {
            writeBlockResponse(response);
            return;
        }

        // 2. OPTIONS 请求（CORS 预检）不限流
        if (!"OPTIONS".equals(request.getMethod())) {
            String counterKey = Const.FLOW_LIMIT_COUNTER + address;
            // 3. 检查请求频率，超限则封禁
            if (!flowUtils.limitPeriodCheck(counterKey, blockKey, BLOCK_TIME, LIMIT, PERIOD)) {
                writeBlockResponse(response);
                return;
            }
        }

        // 4. 放行
        chain.doFilter(request, response);
    }

    /** 返回 429 限流响应 */
    private void writeBlockResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=utf-8");
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(
                RestBean.failure(429, "请求频率过快，请稍后再试")));
    }

    String resolveClientAddress(HttpServletRequest request) {
        String forwardedFor = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor;
        }

        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    private String firstHeaderValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }

        String first = header.split(",", 2)[0].trim();
        if (first.isEmpty() || "unknown".equalsIgnoreCase(first)) {
            return null;
        }
        return first;
    }
}
