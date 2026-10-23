package com.campus.lostfound.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 部分更新用户资料。
 *
 * <p>字段为 {@code null} 表示不修改；空字符串则表示主动清空对应可选字段。</p>
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 50, message = "昵称长度需为 1-50 个字符")
        String nickname,

        @Size(max = 500, message = "头像 URL 不能超过 500 个字符")
        String avatarUrl,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String phone,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱不能超过 100 个字符")
        String email
) {
}

