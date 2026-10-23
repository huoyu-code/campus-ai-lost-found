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
        MDC.put(TraceIds.MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}

