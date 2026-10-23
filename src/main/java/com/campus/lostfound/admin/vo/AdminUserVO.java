package com.campus.lostfound.admin.vo;

import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * 管理员用户列表视图，不包含密码哈希。
 */
public record AdminUserVO(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String phone,
        String email,
        UserRole role,
        UserStatus status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
