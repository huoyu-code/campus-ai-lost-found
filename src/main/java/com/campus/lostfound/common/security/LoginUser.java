package com.campus.lostfound.common.security;

import com.campus.lostfound.user.enums.UserRole;

import java.time.Instant;

/**
 * 从 JWT 恢复出的当前请求用户快照。
 *
 * <p>它只保存认证和授权所需的少量字段，不包含密码等敏感信息。</p>
 */
public record LoginUser(
        Long userId,
        String username,
        String nickname,
        UserRole role,
        String jti,
        Instant issuedAt,
        Instant expiresAt
) {
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}

