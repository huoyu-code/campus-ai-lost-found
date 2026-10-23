package com.campus.lostfound.auth.service;

/**
 * 消费票据后得到的不可伪造连接身份。
 */
public record WebSocketTicketPrincipal(Long userId) {
}
