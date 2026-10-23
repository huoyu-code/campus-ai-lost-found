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
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, exception);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}

