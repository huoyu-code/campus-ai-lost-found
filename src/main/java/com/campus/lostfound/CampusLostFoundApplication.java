package com.campus.lostfound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 后端应用启动入口。
 *
 * <p>启动时由 Spring Boot 扫描当前包及其子包，加载 Web、MyBatis、Redis、
 * 参数校验和安全相关组件，同时扫描 {@code @ConfigurationProperties} 配置类。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusLostFoundApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusLostFoundApplication.class, args);
    }
}

