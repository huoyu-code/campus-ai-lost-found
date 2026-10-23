package com.campus.lostfound.admin.dto;

import com.campus.lostfound.admin.enums.PostModerationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 隐藏帖子或恢复隐藏前状态。
 */
public record ModeratePostRequest(
        @NotNull(message = "处置动作不能为空")
        PostModerationAction action,

        @Size(max = 500, message = "处置原因不能超过 500 个字符")
        String reason
) {
}
