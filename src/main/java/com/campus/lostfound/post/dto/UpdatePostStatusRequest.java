package com.campus.lostfound.post.dto;

import com.campus.lostfound.post.enums.PostStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 发布者关闭帖子或确认完成后使用的状态请求。
 *
 * <p>虽然目标状态是枚举，Service 仍只允许 {@code CLOSED} 和 {@code RESOLVED}。</p>
 */
public record UpdatePostStatusRequest(
        @NotNull(message = "目标状态不能为空")
        PostStatus targetStatus
) {
}

