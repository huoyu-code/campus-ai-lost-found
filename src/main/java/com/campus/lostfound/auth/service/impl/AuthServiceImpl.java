package com.campus.lostfound.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.lostfound.auth.jwt.JwtService;
import com.campus.lostfound.auth.service.AuthService;
import com.campus.lostfound.auth.service.TokenRevocationService;
import com.campus.lostfound.auth.vo.LoginResponse;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.user.dto.LoginRequest;
import com.campus.lostfound.user.dto.RegisterRequest;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import com.campus.lostfound.user.service.UserService;
import com.campus.lostfound.user.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;
    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO register(RegisterRequest request) {
        String username = request.username().trim();
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0) {
            throw ApiException.conflict("用户名已存在");
        }

        String phone = trimToNull(request.phone());
        String email = trimToNull(request.email());
        if (phone != null && userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)) > 0) {
            throw ApiException.conflict("手机号已被使用");
        }
        if (email != null && userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)) > 0) {
            throw ApiException.conflict("邮箱已被使用");
        }

        SysUser user = SysUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname().trim())
                .phone(phone)
                .email(email)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        return userService.toProfile(userMapper.selectById(user.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username().trim())
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("账号已被禁用");
        }

        LocalDateTime now = LocalDateTime.now();
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginAt, now)
                .set(SysUser::getUpdatedAt, now));
        user.setLastLoginAt(now);

        JwtService.IssuedToken issuedToken = jwtService.issue(user);
        return new LoginResponse(
                issuedToken.token(),
                "Bearer",
                issuedToken.expiresAt(),
                userService.toProfile(user)
        );
    }

    @Override
    public void logout(LoginUser user) {
        tokenRevocationService.revoke(user);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

