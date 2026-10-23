package com.campus.lostfound.auth.service;

import com.campus.lostfound.auth.config.JwtProperties;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT 即时吊销服务。
 *
 * <p>退出登录按 {@code jti} 吊销单个令牌；禁用用户时记录用户级失效时间，
 * 使该时间之前签发的全部令牌立即失效。Redis 键均按令牌生命周期设置过期时间。</p>
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String TOKEN_KEY_PREFIX = "auth:revoked:jti:";
    private static final String USER_INVALID_AFTER_KEY_PREFIX =
            "auth:user-invalid-after:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public boolean isRevoked(LoginUser user) {
        try {
            Boolean revoked = redisTemplate.hasKey(TOKEN_KEY_PREFIX + user.jti());
            if (Boolean.TRUE.equals(revoked)) {
                return true;
            }
            String invalidAfterValue = redisTemplate.opsForValue().get(
                    USER_INVALID_AFTER_KEY_PREFIX + user.userId());
            if (invalidAfterValue == null) {
                return false;
            }
            long invalidAfterMillis = Long.parseLong(invalidAfterValue);
            return user.issuedAt().toEpochMilli() <= invalidAfterMillis;
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("认证服务暂不可用，请稍后重试");
        } catch (NumberFormatException exception) {
            // 无法读取安全状态时按吊销处理，避免静默放行旧令牌。
            return true;
        }
    }

    public void revoke(LoginUser user) {
        Duration ttl = Duration.between(Instant.now(), user.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            // 只保留到令牌原本过期为止，之后即使键不存在也不会再接受该令牌。
            redisTemplate.opsForValue().set(
                    TOKEN_KEY_PREFIX + user.jti(), "1", ttl);
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("退出认证服务暂不可用，请稍后重试");
        }
    }

    /**
     * 记录用户级失效时间。重新启用账号后，只有该时间之后签发的令牌才有效。
     */
    public void invalidateAllForUser(Long userId) {
        Duration ttl = Duration.ofMinutes(jwtProperties.ttlMinutes()).plusMinutes(1);
        try {
            redisTemplate.opsForValue().set(
                    USER_INVALID_AFTER_KEY_PREFIX + userId,
                    String.valueOf(Instant.now().toEpochMilli()),
                    ttl);
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("账号状态服务暂不可用，请稍后重试");
        }
    }
}

