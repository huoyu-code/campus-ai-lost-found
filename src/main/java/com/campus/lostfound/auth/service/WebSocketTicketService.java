package com.campus.lostfound.auth.service;

import com.campus.lostfound.auth.config.WebSocketProperties;
import com.campus.lostfound.auth.vo.WebSocketTicketVO;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Redis 一次性 WebSocket 票据服务。
 *
 * <p>浏览器不能安全地在 WebSocket URL 中携带长期 JWT，因此先使用 HTTP Bearer Token
 * 换取短时票据。票据通过 Redis Lua 原子读取并删除，确保只能消费一次。</p>
 */
@Service
@RequiredArgsConstructor
public class WebSocketTicketService {

    private static final String KEY_PREFIX = "ws:ticket:";
    private static final DefaultRedisScript<String> CONSUME_SCRIPT =
            new DefaultRedisScript<>("""
                    local value = redis.call('GET', KEYS[1])
                    if value then
                        redis.call('DEL', KEYS[1])
                    end
                    return value
                    """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final WebSocketProperties properties;
    private final SysUserMapper userMapper;
    private final TokenRevocationService tokenRevocationService;

    public WebSocketTicketVO create(LoginUser loginUser) {
        try {
            // 票据只保存恢复认证所需的 ID、jti 和时间，不重复保存昵称等用户资料。
            String ticket = UUID.randomUUID().toString().replace("-", "");
            String payload = String.join("|",
                    String.valueOf(loginUser.userId()),
                    loginUser.jti(),
                    String.valueOf(loginUser.issuedAt().toEpochMilli()),
                    String.valueOf(loginUser.expiresAt().toEpochMilli()));
            Duration ttl = Duration.ofSeconds(properties.ticketTtlSeconds());
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket, payload, ttl);
            return new WebSocketTicketVO(
                    ticket,
                    properties.ticketTtlSeconds(),
                    Instant.now().plus(ttl)
            );
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("WebSocket 票据服务暂不可用");
        }
    }

    /**
     * 原子消费票据。无论后续校验是否成功，票据都已经删除，不能重复使用。
     */
    public WebSocketTicketPrincipal consume(String ticket) {
        if (ticket == null || !ticket.matches("^[a-f0-9]{32}$")) {
            throw ApiException.unauthorized("WebSocket 票据无效");
        }
        String payload;
        try {
            payload = redisTemplate.execute(
                    CONSUME_SCRIPT, List.of(KEY_PREFIX + ticket));
        } catch (DataAccessException exception) {
            throw ApiException.serviceUnavailable("WebSocket 认证服务暂不可用");
        }
        if (payload == null) {
            throw ApiException.unauthorized("WebSocket 票据已失效或已被使用");
        }

        Long userId;
        String jti;
        Instant issuedAt;
        Instant expiresAt;
        try {
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("invalid ticket payload");
            }
            userId = Long.valueOf(parts[0]);
            jti = parts[1];
            issuedAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));
            expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[3]));
        } catch (IllegalArgumentException exception) {
            throw ApiException.unauthorized("WebSocket 票据无效");
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw ApiException.unauthorized("登录凭证已过期，请重新登录");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("登录用户不存在或已被禁用");
        }
        // 使用数据库中的最新角色和资料重建认证快照，再检查原 JWT 是否被吊销。
        LoginUser loginUser = new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                jti,
                issuedAt,
                expiresAt
        );
        if (tokenRevocationService.isRevoked(loginUser)) {
            throw ApiException.unauthorized("登录凭证已失效");
        }
        return new WebSocketTicketPrincipal(user.getId());
    }
}
