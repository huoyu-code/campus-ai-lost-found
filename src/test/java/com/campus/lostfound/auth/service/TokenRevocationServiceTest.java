package com.campus.lostfound.auth.service;

import com.campus.lostfound.auth.config.JwtProperties;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证单令牌吊销和用户级令牌失效的 Redis 判断逻辑。
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(
                redisTemplate,
                new JwtProperties("01234567890123456789012345678901", 60)
        );
    }

    @Test
    void detectsSingleTokenRevocationFirst() {
        LoginUser user = user(7L, "jti-1", Instant.parse("2026-10-23T10:00:00Z"));
        when(redisTemplate.hasKey("auth:revoked:jti:jti-1")).thenReturn(true);

        assertThat(service.isRevoked(user)).isTrue();
    }

    @Test
    void comparesTokenIssueTimeWithUserInvalidationTime() {
        LoginUser user = user(7L, "jti-2", Instant.parse("2026-10-23T10:00:00Z"));
        when(redisTemplate.hasKey("auth:revoked:jti:jti-2")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:user-invalid-after:7"))
                .thenReturn(String.valueOf(
                        Instant.parse("2026-10-23T10:01:00Z").toEpochMilli()));

        assertThat(service.isRevoked(user)).isTrue();

        LoginUser newerToken = user(
                7L, "jti-3", Instant.parse("2026-10-23T10:02:00Z"));
        when(redisTemplate.hasKey("auth:revoked:jti:jti-3")).thenReturn(false);
        assertThat(service.isRevoked(newerToken)).isFalse();
    }

    @Test
    void storesUserInvalidationTimestampWithMaximumTokenLifetime() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.invalidateAllForUser(9L);

        verify(valueOperations).set(
                eq("auth:user-invalid-after:9"),
                anyString(),
                eq(Duration.ofMinutes(61))
        );
    }

    private LoginUser user(Long userId, String jti, Instant issuedAt) {
        return new LoginUser(
                userId,
                "user" + userId,
                "用户",
                UserRole.USER,
                jti,
                issuedAt,
                issuedAt.plus(Duration.ofHours(1))
        );
    }
}
