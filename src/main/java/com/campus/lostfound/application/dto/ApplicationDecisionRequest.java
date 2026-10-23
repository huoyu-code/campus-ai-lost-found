package com.campus.lostfound.application.dto;

import com.campus.lostfound.application.enums.ApplicationDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 接受或拒绝申请的处理参数。
 */
public record ApplicationDecisionRequest(
        @NotNull(message = "处理决定不能为空")
        ApplicationDecision decision,

        @Size(max = 500, message = "处理意见不能超过 500 个字符")
        String processNote
) {
}
