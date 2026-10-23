package com.campus.lostfound.chat.websocket;

/**
 * 统一 WebSocket JSON envelope。
 *
 * @param type 消息类型
 * @param requestId 客户端请求标识；服务端主动推送可以为空
 * @param data 对应类型的业务数据
 */
public record WebSocketEvent<T>(
        WebSocketEventType type,
        String requestId,
        T data
) {
}
