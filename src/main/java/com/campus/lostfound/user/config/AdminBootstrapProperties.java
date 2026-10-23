package com.campus.lostfound.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String username,
        String password,
        String nickname
) {
}

