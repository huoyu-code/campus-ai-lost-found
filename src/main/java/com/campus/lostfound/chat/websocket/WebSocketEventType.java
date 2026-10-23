package com.campus.lostfound.chat.websocket;

/**
 * WebSocket envelope 类型。
 *
 * <p>客户端只允许发送 {@code PING} 和 {@code CHAT_MESSAGE}；其余类型用于服务端响应或推送。</p>
 */
public enum WebSocketEventType {
    PING,
    PONG,
    CHAT_MESSAGE,
    CHAT_MESSAGE_ACK,
    NOTIFICATION,
    ERROR
}
