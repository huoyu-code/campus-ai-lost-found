package com.campus.lostfound.chat.service;

import com.campus.lostfound.chat.vo.ChatConversationVO;

/**
 * 会话创建结果，区分新建和幂等返回已有会话。
 */
public record ConversationCreationResult(
        ChatConversationVO conversation,
        boolean created
) {
}
