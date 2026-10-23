package com.campus.lostfound.chat.service;

import com.campus.lostfound.chat.vo.ChatMessageVO;

/**
 * 消息发送结果。
 */
public record MessageSendResult(
        ChatMessageVO message,
        boolean duplicate
) {
}
