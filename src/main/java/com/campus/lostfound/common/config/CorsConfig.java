package com.campus.lostfound.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

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
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("campusCorsFilter");
        return registration;
    }
}

