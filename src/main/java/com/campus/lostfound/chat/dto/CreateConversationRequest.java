package com.campus.lostfound.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 创建会话请求，只传帖子和对端用户，当前操作者来自 JWT。
 */
public record CreateConversationRequest(
        @NotNull(message = "帖子 ID 不能为空")
        @Positive(message = "帖子 ID 必须大于 0")
        Long postId,

        @NotNull(message = "对端用户 ID 不能为空")
        @Positive(message = "对端用户 ID 必须大于 0")
        Long peerUserId
) {
}
