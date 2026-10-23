package com.campus.lostfound.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * WebSocket {@code CHAT_MESSAGE.data} 的结构。
 */
public record SendChatMessagePayload(
        @NotNull(message = "会话 ID 不能为空")
        @Positive(message = "会话 ID 必须大于 0")
        Long conversationId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容不能超过 2000 个字符")
        String content
) {
}
