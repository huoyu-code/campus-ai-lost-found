package com.campus.lostfound.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.user.dto.ChangePasswordRequest;
import com.campus.lostfound.user.dto.UpdateProfileRequest;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.mapper.SysUserMapper;
import com.campus.lostfound.user.service.UserService;
import com.campus.lostfound.user.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户资料业务实现。
 *
 * <p>用户接口只返回 {@link UserProfileVO}，不会把包含密码哈希的数据库实体直接暴露给前端。</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        return user;
    }

    @Override
    public UserProfileVO getProfile(Long userId) {
        return toProfile(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateProfile(Long userId, UpdateProfileRequest request) {
        SysUser user = requireUser(userId);
        // 只 set 非 null 字段，实现部分更新语义。
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getUpdatedAt, LocalDateTime.now());

        if (request.nickname() != null) {
            String nickname = request.nickname().trim();
            if (nickname.isEmpty()) {
                throw ApiException.badRequest("昵称不能为空");
            }
            update.set(SysUser::getNickname, nickname);
            user.setNickname(nickname);
        }
        if (request.avatarUrl() != null) {
            String avatarUrl = trimToNull(request.avatarUrl());
            update.set(SysUser::getAvatarUrl, avatarUrl);
            user.setAvatarUrl(avatarUrl);
        }
        if (request.phone() != null) {
            String phone = trimToNull(request.phone());
            update.set(SysUser::getPhone, phone);
            user.setPhone(phone);
        }
        if (request.email() != null) {
            String email = trimToNull(request.email());
            update.set(SysUser::getEmail, email);
            user.setEmail(email);
        }

        if (userMapper.update(null, update) != 1) {
            throw ApiException.conflict("用户资料已变化，请刷新后重试");
        }
        return toProfile(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = requireUser(userId);
        // 修改密码前必须验证原密码，防止令牌泄露后被直接替换密码。
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("新密码不能与原密码相同");
        }

        int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPasswordHash, passwordEncoder.encode(request.newPassword()))
                .set(SysUser::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw ApiException.conflict("密码修改失败，请刷新后重试");
        }
    }

    @Override
    public UserProfileVO toProfile(SysUser user) {
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

