package com.campus.lostfound.user.vo;

import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;

import java.time.LocalDateTime;

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

