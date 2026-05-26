package com.msz.resume.ai.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.auth.util.FlowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowLimitFilterTest {

    private final FlowLimitFilter filter = new FlowLimitFilter(mock(FlowUtils.class), new ObjectMapper());

    @Test
    @DisplayName("优先使用 X-Forwarded-For 中的首个客户端 IP")
    void resolveClientAddressUsesFirstForwardedForIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("112.94.129.45, 172.22.0.1");
        when(request.getHeader("X-Real-IP")).thenReturn("172.22.0.1");
        when(request.getRemoteAddr()).thenReturn("172.22.0.2");

        assertEquals("112.94.129.45", filter.resolveClientAddress(request));
    }

    @Test
    @DisplayName("没有 X-Forwarded-For 时使用 X-Real-IP")
    void resolveClientAddressFallsBackToRealIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("112.94.129.45");
        when(request.getRemoteAddr()).thenReturn("172.22.0.2");

        assertEquals("112.94.129.45", filter.resolveClientAddress(request));
    }

    @Test
    @DisplayName("没有代理头时使用 remoteAddr")
    void resolveClientAddressFallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
        when(request.getHeader("X-Real-IP")).thenReturn(" ");
        when(request.getRemoteAddr()).thenReturn("172.22.0.2");

        assertEquals("172.22.0.2", filter.resolveClientAddress(request));
    }
}
