package com.campus.lostfound.chat.websocket;

import java.time.Instant;

/**
 * 心跳响应时间。
 */
public record PongData(Instant timestamp) {
}
