package com.campus.lostfound.user.vo;

import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * 对外用户资料视图，刻意不包含密码哈希。
 */
public record UserProfileVO(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String phone,
        String email,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt
) {
}

