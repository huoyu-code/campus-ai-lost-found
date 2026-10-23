package com.campus.lostfound.auth.config;

import com.campus.lostfound.auth.interceptor.AuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 认证拦截规则。
 *
 * <p>JWT 过滤器负责“解析是谁”，本拦截器负责“是否允许访问”。默认所有接口都要求登录，
 * 仅显式列出的公开接口和被拦截器识别的公开帖子 GET 请求可以匿名访问。</p>
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/**")
                // 默认拒绝比逐条列受保护接口更安全，新增接口不会意外公开。
                .excludePathPatterns(
                        "/auth/register",
                        "/auth/login",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/error"
                );
    }
}

