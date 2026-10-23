package com.campus.lostfound.common.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Trace ID 在请求属性、HTTP 头和日志中的统一键名。
 */
public final class TraceIds {

    public static final String ATTRIBUTE = "traceId";
    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceIds() {
    }

    public static String get(HttpServletRequest request) {
        Object traceId = request.getAttribute(ATTRIBUTE);
        return traceId instanceof String value && StringUtils.hasText(value) ? value : "";
    }
}

