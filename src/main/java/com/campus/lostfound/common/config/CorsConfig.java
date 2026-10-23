package com.campus.lostfound.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * 前后端分离开发时的跨域配置。
 *
 * <p>只允许配置文件中声明的来源。浏览器预检请求必须最先处理，
 * 因此 CorsFilter 使用最高优先级。</p>
 */
@Configuration
public class CorsConfig {

    private final String allowedOrigins;

    public CorsConfig(
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
            String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> campusCorsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split("\\s*,\\s*"))
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "X-Trace-Id"));
        configuration.setExposedHeaders(Arrays.asList("X-Trace-Id"));
        // JWT 通过 Authorization 头传递而不是 Cookie，但仍保留凭证支持以适配后续前端能力。
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        // 预检请求应在认证过滤器和 MVC 之前返回，避免被误判为未登录。
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("campusCorsFilter");
        return registration;
    }
}

