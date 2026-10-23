package com.campus.lostfound.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 自定义 JWT 配置。
 *
 * <p>配置前缀为 {@code app.jwt}。密钥必须来自环境变量且至少 32 字节，
 * 有效期以分钟表示，Spring Boot 会在启动阶段完成校验。</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET 必须配置") String secret,
        @Min(value = 1, message = "JWT 有效期必须大于 0") long ttlMinutes
) {
}

