package com.campus.lostfound.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * WebSocket 票据和发送参数。
 *
 * <p>票据只用于浏览器握手，有效期很短且只能消费一次。发送超时和缓冲上限用于
 * 防止慢连接无限占用服务端线程和内存。</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(
        @Min(value = 5, message = "WebSocket 票据有效期不能小于 5 秒")
        @Max(value = 120, message = "WebSocket 票据有效期不能超过 120 秒")
        int ticketTtlSeconds,

        @Min(value = 1000, message = "WebSocket 发送超时不能小于 1000 毫秒")
        int sendTimeLimitMillis,

        @Min(value = 8192, message = "WebSocket 发送缓冲不能小于 8192 字节")
        int sendBufferSizeBytes
) {
}
