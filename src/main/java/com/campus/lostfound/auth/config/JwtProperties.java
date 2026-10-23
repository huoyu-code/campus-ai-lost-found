package com.campus.lostfound.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET 必须配置") String secret,
        @Min(value = 1, message = "JWT 有效期必须大于 0") long ttlMinutes
) {
}

