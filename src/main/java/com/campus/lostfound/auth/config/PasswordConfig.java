package com.campus.lostfound.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希配置。
 *
 * <p>只使用 Spring Security 的 BCrypt 实现，不引入完整 Spring Security 过滤链，
 * 认证流程仍由项目自定义的 JWT 组件负责。</p>
 */
@Configuration
public class PasswordConfig {

    /**
     * 强度 12 兼顾当前开发机性能和暴力破解成本。
     * 调整强度不需要修改数据库字段，因为 BCrypt 哈希本身携带参数。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

