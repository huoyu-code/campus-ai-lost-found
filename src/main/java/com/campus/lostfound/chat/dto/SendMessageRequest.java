package com.campus.lostfound.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * REST 发送消息参数。
 *
 * <p>客户端为每次逻辑发送生成稳定 {@code requestId}，网络重试时继续使用同一个值。</p>
 */
public record SendMessageRequest(
        @NotBlank(message = "requestId 不能为空")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{1,64}$",
                message = "requestId 只能包含字母、数字、下划线和短横线，且不超过 64 个字符")
        String requestId,

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容不能超过 2000 个字符")
        String content
) {
}
