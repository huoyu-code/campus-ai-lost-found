package com.campus.lostfound.chat.websocket;

import com.campus.lostfound.common.api.ApiCode;

/**
 * WebSocket 业务错误，不返回堆栈或 SQL。
 */
public record WebSocketErrorData(
        ApiCode code,
        String message
) {
}
