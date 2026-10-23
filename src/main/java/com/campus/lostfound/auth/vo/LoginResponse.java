package com.campus.lostfound.auth.vo;

import com.campus.lostfound.user.vo.UserProfileVO;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileVO user
) {
}

