package com.campus.lostfound.auth.vo;

import java.time.Instant;

/**
 * 一次性 WebSocket 握手票据。
 */
public record WebSocketTicketVO(
        String ticket,
        int expiresInSeconds,
        Instant expiresAt
) {
}
