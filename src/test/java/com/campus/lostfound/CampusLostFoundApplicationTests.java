package com.campus.lostfound;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 冒烟测试：确认 Spring 应用上下文和核心配置可以完整加载。
 */
@SpringBootTest
@ActiveProfiles("test")
class CampusLostFoundApplicationTests {

    @Test
    void contextLoads() {
    }
}

