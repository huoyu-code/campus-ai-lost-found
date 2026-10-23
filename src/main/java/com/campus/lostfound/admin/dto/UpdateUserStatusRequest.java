package com.campus.lostfound.admin.dto;

import com.campus.lostfound.user.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员启用或禁用用户账号。
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "目标用户状态不能为空")
        UserStatus status,

        @Size(max = 500, message = "操作原因不能超过 500 个字符")
        String reason
) {
}
