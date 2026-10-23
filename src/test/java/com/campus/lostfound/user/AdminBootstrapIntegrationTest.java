package com.campus.lostfound.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.bootstrap-admin.enabled=true",
        "app.bootstrap-admin.username=bootstrap_admin",
        "app.bootstrap-admin.password=Bootstrap2026",
        "app.bootstrap-admin.nickname=测试管理员"
})
@ActiveProfiles("test")
class AdminBootstrapIntegrationTest {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdminWithHashedPassword() {
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, "bootstrap_admin"));

        assertThat(admin).isNotNull();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isNotEqualTo("Bootstrap2026");
        assertThat(passwordEncoder.matches("Bootstrap2026", admin.getPasswordHash())).isTrue();
    }
}

