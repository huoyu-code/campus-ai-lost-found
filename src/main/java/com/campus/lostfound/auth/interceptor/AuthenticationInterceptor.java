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

/**
 * 登录访问控制拦截器。
 *
 * <p>帖子列表、帖子详情和公开搜索允许匿名访问；其余未显式排除的接口默认要求登录。
 * 管理员权限仍由具体 Service 再次校验，拦截器不替代业务授权。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final Pattern PUBLIC_POST_GET = Pattern.compile("/posts(/\\d+)?");
    private static final Pattern PUBLIC_SEARCH_GET = Pattern.compile("/search/posts");

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
        // 只开放帖子浏览和搜索；申请列表、管理搜索等相似路径仍要求登录。
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PUBLIC_POST_GET.matcher(path).matches()
                || PUBLIC_SEARCH_GET.matcher(path).matches();
    }

    private void writeError(HttpServletResponse response, ApiException exception,
                            String traceId) throws Exception {
        // Interceptor 位于 MVC 异常处理链之前，需要自行写出统一响应体。
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

