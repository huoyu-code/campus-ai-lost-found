package com.campus.lostfound.auth.filter;

import com.campus.lostfound.auth.jwt.JwtService;
import com.campus.lostfound.auth.service.TokenRevocationService;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.common.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 无状态 JWT 解析过滤器。
 *
 * <p>过滤器只负责解析令牌和建立 {@link UserContext}，不会直接拒绝匿名请求。
 * 是否允许匿名访问交给后续拦截器判断，这样公开 GET 接口仍可正常访问。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "authError";

    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = jwtService.resolveToken(request.getHeader(JwtService.HEADER));
            if (StringUtils.hasText(token)) {
                try {
                    LoginUser user = jwtService.parse(token);
                    if (tokenRevocationService.isRevoked(user)) {
                        request.setAttribute(AUTH_ERROR_ATTRIBUTE,
                                ApiException.unauthorized("登录状态已失效，请重新登录"));
                    } else {
                        UserContext.set(user);
                    }
                } catch (ApiException exception) {
                    // 先记录错误，后续拦截器可保持统一 JSON 错误格式。
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, exception);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 线程池会复用线程，必须清理 ThreadLocal，防止身份串到其他请求。
            UserContext.clear();
        }
    }
}

