package com.campus.lostfound.post.dto;

import com.campus.lostfound.post.enums.PostStatus;
import jakarta.validation.constraints.NotNull;

public record UpdatePostStatusRequest(
        @NotNull(message = "目标状态不能为空")
        PostStatus targetStatus
) {
}

