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

@Component
public class JwtService {

    public static final String HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private static final String ISSUER = "campus-lost-found";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_NICKNAME = "nickname";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey secretKey;
    private final Duration tokenTtl;

    public JwtService(JwtProperties properties) {
        if (!StringUtils.hasText(properties.secret())
                || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must contain at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8));
        this.tokenTtl = Duration.ofMinutes(properties.ttlMinutes());
    }

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
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new IssuedToken(token, expiresAt, jti);
    }

    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new LoginUser(
                    ((Number) claims.get(CLAIM_USER_ID)).longValue(),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_NICKNAME, String.class),
                    UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    claims.getId(),
                    claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException exception) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw ApiException.unauthorized("登录凭证无效，请重新登录");
        }
    }

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

