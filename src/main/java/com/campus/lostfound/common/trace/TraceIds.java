package com.campus.lostfound.common.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

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

