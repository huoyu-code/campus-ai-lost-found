package com.campus.lostfound.auth.vo;

import com.campus.lostfound.user.vo.UserProfileVO;

import java.time.Instant;

/**
 * 登录成功响应，只包含访问令牌和脱敏后的用户资料。
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileVO user
) {
}

