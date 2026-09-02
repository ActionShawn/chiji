// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\web\TraceIdFilter.java
package com.chiji.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * 请求链路 ID 过滤器。
 * <p>
 * 为每个请求生成唯一 traceId 写入 MDC，随日志输出（logback pattern 中的 {@code %X{traceId}}），
 * 同时通过响应头 {@code X-Trace-Id} 回传客户端，便于问题追踪；请求结束后清理 MDC 防止线程复用串号。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 处理单个请求：优先复用请求头中的 traceId，否则生成新值。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = Optional.ofNullable(request.getHeader(TRACE_ID_HEADER))
                .filter(v -> !v.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
        MDC.put("traceId", traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
