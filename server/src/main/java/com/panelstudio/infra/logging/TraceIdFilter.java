package com.panelstudio.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求建立 MDC 上下文：requestId（链路）+ deviceId（匿名身份）。
 * 日志字段固定 requestId / deviceId / path，便于按链路排查。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_DEVICE_ID = "X-Device-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_DEVICE_ID = "deviceId";
    public static final String MDC_PATH = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        String deviceId = request.getHeader(HEADER_DEVICE_ID);
        MDC.put(MDC_REQUEST_ID, requestId);
        if (deviceId != null && !deviceId.isBlank()) {
            MDC.put(MDC_DEVICE_ID, deviceId);
        }
        MDC.put(MDC_PATH, request.getRequestURI());
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_DEVICE_ID);
            MDC.remove(MDC_PATH);
        }
    }
}
