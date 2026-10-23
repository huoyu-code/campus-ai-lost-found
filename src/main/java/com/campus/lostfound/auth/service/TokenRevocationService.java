package com.campus.lostfound.auth.service;

import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String TOKEN_KEY_PREFIX = "auth:revoked:jti:";

    private final StringRedisTemplate redisTemplate;

    public boolean isRevoked(LoginUser user) {
        try {
            Boolean revoked = redisTemplate.hasKey(TOKEN_KEY_PREFIX + user.jti());
            return Boolean.TRUE.equals(revoked);
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("认证服务暂不可用，请稍后重试");
        }
    }

    public void revoke(LoginUser user) {
        Duration ttl = Duration.between(Instant.now(), user.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    TOKEN_KEY_PREFIX + user.jti(), "1", ttl);
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("退出认证服务暂不可用，请稍后重试");
        }
    }
}

