package com.campus.lostfound.chat.websocket;

import com.campus.lostfound.chat.vo.ChatMessageVO;

/**
 * 聊天消息持久化后的确认数据。
 */
public record ChatMessageAck(
        ChatMessageAckStatus status,
        ChatMessageVO message
) {
}
