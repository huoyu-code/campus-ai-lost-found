package com.campus.lostfound.common.security;

import com.campus.lostfound.user.enums.UserRole;

import java.time.Instant;

public record LoginUser(
        Long userId,
        String username,
        String nickname,
        UserRole role,
        String jti,
        Instant expiresAt
) {
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}

