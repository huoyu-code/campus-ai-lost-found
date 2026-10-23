package com.campus.lostfound.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个 HTTP 请求建立 Trace ID。
 *
 * <p>客户端可传入不超过 64 字符的 X-Trace-Id；未传入时由服务端生成。
 * Trace ID 同时写入响应头、MDC 日志和统一响应体，便于串联一次请求。</p>
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TraceIds.HEADER);
        String traceId = StringUtils.hasText(incoming)
                ? incoming.substring(0, Math.min(incoming.length(), 64))
                : UUID.randomUUID().toString();

        request.setAttribute(TraceIds.ATTRIBUTE, traceId);
        response.setHeader(TraceIds.HEADER, traceId);
        // 日志模板中的 traceId 从 MDC 读取，不需要在每个 log 调用中手动传参。
        MDC.put(TraceIds.MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程复用前清理日志上下文。
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}

