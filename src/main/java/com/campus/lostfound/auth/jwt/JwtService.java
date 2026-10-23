package com.campus.lostfound.auth.jwt;

import com.campus.lostfound.auth.config.JwtProperties;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 自定义 JWT 的签发与校验组件。
 *
 * <p>令牌包含用户 ID、用户名、昵称、角色和唯一 {@code jti}。业务数据不直接信任
 * 前端传入的用户身份，认证信息只从签名有效的令牌中读取。</p>
 */
@Component
public class JwtService {

    public static final String HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private static final String ISSUER = "campus-lost-found";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ISSUED_AT_MILLIS = "iatMillis";

    private final SecretKey secretKey;
    private final Duration tokenTtl;

    public JwtService(JwtProperties properties) {
        // JJWT 的 HMAC 密钥长度由算法决定，项目统一要求至少 256 bit。
        if (!StringUtils.hasText(properties.secret())
                || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must contain at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8));
        this.tokenTtl = Duration.ofMinutes(properties.ttlMinutes());
    }

    /**
     * 为已完成密码校验的用户签发访问令牌。
     */
    public IssuedToken issue(SysUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(tokenTtl);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_NICKNAME, user.getNickname())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_ISSUED_AT_MILLIS, now.toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new IssuedToken(token, expiresAt, jti);
    }

    /**
     * 校验签名、Issuer 和过期时间，并转换成请求上下文使用的用户快照。
     *
     * <p>这里不查询数据库；退出登录产生的即时失效由 Redis 中的 {@code jti} 黑名单负责。</p>
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number issuedAtMillis = claims.get(CLAIM_ISSUED_AT_MILLIS, Number.class);
            Instant issuedAt = issuedAtMillis == null
                    ? claims.getIssuedAt().toInstant()
                    : Instant.ofEpochMilli(issuedAtMillis.longValue());

            return new LoginUser(
                    ((Number) claims.get(CLAIM_USER_ID)).longValue(),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_NICKNAME, String.class),
                    UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    claims.getId(),
                    issuedAt,
                    claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException exception) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw ApiException.unauthorized("登录凭证无效，请重新登录");
        }
    }

    /**
     * 兼容标准的 {@code Bearer <token>} 格式，同时允许本地调试时只传 token。
     */
    public String resolveToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        if (authorizationHeader.startsWith(TOKEN_PREFIX)) {
            return authorizationHeader.substring(TOKEN_PREFIX.length()).trim();
        }
        return authorizationHeader.trim();
    }

    public record IssuedToken(String token, Instant expiresAt, String jti) {
    }
}

