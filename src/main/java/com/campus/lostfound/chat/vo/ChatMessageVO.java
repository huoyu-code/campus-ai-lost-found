package com.campus.lostfound.chat.vo;

import com.campus.lostfound.post.vo.UserSummaryVO;

import java.time.LocalDateTime;

/**
 * 聊天消息对外视图，不暴露密码、邮箱或手机号。
 */
public record ChatMessageVO(
        Long id,
        Long conversationId,
        String requestId,
        UserSummaryVO sender,
        String content,
        LocalDateTime createdAt
) {
}
