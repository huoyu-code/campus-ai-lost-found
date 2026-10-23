package com.campus.lostfound.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理员首次初始化配置。
 *
 * <p>默认关闭。用户名和密码从环境变量读取，不应写入配置文件或数据库脚本。</p>
 */
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record AdminBootstrapProperties(
        boolean enabled,
        String username,
        String password,
        String nickname
) {
}

