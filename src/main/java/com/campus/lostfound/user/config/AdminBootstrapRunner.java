package com.campus.lostfound.user.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 应用启动时按需创建首个管理员账号。
 *
 * <p>只有显式启用后才执行；用户名已存在时直接跳过，保证多次启动不会重复创建。
 * 密码仍经过 BCrypt 哈希后再落库。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.username())
                || !StringUtils.hasText(properties.password())) {
            log.warn("管理员初始化已启用，但 ADMIN_USERNAME 或 ADMIN_PASSWORD 未配置");
            return;
        }

        String username = properties.username().trim();
        if (!username.matches("^[A-Za-z0-9_]{3,20}$")) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME must contain 3-20 letters, digits, or underscores");
        }
        if (properties.password().length() < 8
                || !properties.password().matches(".*[A-Za-z].*")
                || !properties.password().matches(".*\\d.*")) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least 8 characters and contain letters and digits");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0) {
            // 初始化是幂等操作：允许应用反复启动，不覆盖已有账号密码。
            log.info("管理员初始化跳过：用户名 {} 已存在", username);
            return;
        }

        String nickname = StringUtils.hasText(properties.nickname())
                ? properties.nickname().trim()
                : "系统管理员";
        LocalDateTime now = LocalDateTime.now();
        SysUser admin = SysUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(properties.password()))
                .nickname(nickname)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userMapper.insert(admin);
        // 日志只记录用户名，绝不能记录管理员密码或密码哈希。
        log.info("管理员账号 {} 已初始化", username);
    }
}

