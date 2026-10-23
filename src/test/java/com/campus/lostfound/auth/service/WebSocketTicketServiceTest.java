package com.campus.lostfound.auth.service;

import com.campus.lostfound.auth.config.WebSocketProperties;
import com.campus.lostfound.auth.vo.WebSocketTicketVO;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一次性 WebSocket 票据的编码、时效和认证快照恢复测试。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketTicketServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private TokenRevocationService tokenRevocationService;

    private WebSocketTicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new WebSocketTicketService(
                redisTemplate,
                new WebSocketProperties(30, 5000, 65536),
                userMapper,
                tokenRevocationService
        );
    }

    @Test
    void createsShortLivedPayloadWithoutProfileFields() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        LoginUser loginUser = loginUser(Instant.now().plusSeconds(3600));

        WebSocketTicketVO ticket = ticketService.create(loginUser);

        assertThat(ticket.ticket()).matches("^[a-f0-9]{32}$");
        assertThat(ticket.expiresInSeconds()).isEqualTo(30);
        verify(valueOperations).set(
                eq("ws:ticket:" + ticket.ticket()),
                eq(loginUser.userId() + "|" + loginUser.jti() + "|"
                        + loginUser.issuedAt().toEpochMilli() + "|"
                        + loginUser.expiresAt().toEpochMilli()),
                eq(Duration.ofSeconds(30))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumesTicketAndRestoresCurrentDatabaseUser() {
        String ticket = "a".repeat(32);
        Instant issuedAt = Instant.now().minusSeconds(5);
        Instant expiresAt = Instant.now().plusSeconds(300);
        String payload = "9|ticket-jti|" + issuedAt.toEpochMilli()
                + "|" + expiresAt.toEpochMilli();
        when(redisTemplate.execute(
                any(RedisScript.class), eq(List.of("ws:ticket:" + ticket))))
                .thenReturn(payload);
        when(userMapper.selectById(9L)).thenReturn(SysUser.builder()
                .id(9L)
                .username("ticket_user")
                .nickname("票据用户")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        when(tokenRevocationService.isRevoked(any(LoginUser.class))).thenReturn(false);

        WebSocketTicketPrincipal principal = ticketService.consume(ticket);

        assertThat(principal.userId()).isEqualTo(9L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsExpiredOrAlreadyConsumedTicket() {
        String ticket = "b".repeat(32);
        String expiredPayload = "9|ticket-jti|"
                + Instant.now().minusSeconds(60).toEpochMilli()
                + "|" + Instant.now().minusSeconds(1).toEpochMilli();
        when(redisTemplate.execute(
                any(RedisScript.class), eq(List.of("ws:ticket:" + ticket))))
                .thenReturn(expiredPayload);

        assertThatThrownBy(() -> ticketService.consume(ticket))
                .isInstanceOf(ApiException.class)
                .hasMessage("登录凭证已过期，请重新登录");

        String consumedTicket = "c".repeat(32);
        when(redisTemplate.execute(
                any(RedisScript.class), eq(List.of("ws:ticket:" + consumedTicket))))
                .thenReturn(null);
        assertThatThrownBy(() -> ticketService.consume(consumedTicket))
                .isInstanceOf(ApiException.class)
                .hasMessage("WebSocket 票据已失效或已被使用");
    }

    private LoginUser loginUser(Instant expiresAt) {
        return new LoginUser(
                9L,
                "ticket_user",
                "票据用户",
                UserRole.USER,
                "ticket-jti",
                Instant.now(),
                expiresAt
        );
    }
}
