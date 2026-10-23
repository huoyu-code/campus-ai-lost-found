package com.campus.lostfound.auth.interceptor;

import com.campus.lostfound.auth.filter.JwtAuthenticationFilter;
import com.campus.lostfound.common.api.ApiCode;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final Pattern PUBLIC_POST_GET = Pattern.compile("/posts(/\\d+)?");

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || isPublicGet(request)
                || UserContext.get() != null) {
            return true;
        }

        Object error = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        if (error instanceof ApiException apiException) {
            writeError(response, apiException, TraceIds.get(request));
            return false;
        }
        writeError(response, ApiException.unauthorized("请先登录"), TraceIds.get(request));
        return false;
    }

    private boolean isPublicGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PUBLIC_POST_GET.matcher(path).matches();
    }

    private void writeError(HttpServletResponse response, ApiException exception,
                            String traceId) throws Exception {
        response.setStatus(exception.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(
                exception.getCode() == null ? ApiCode.INTERNAL_ERROR : exception.getCode(),
                exception.getMessage(),
                traceId
        ));
    }
}

