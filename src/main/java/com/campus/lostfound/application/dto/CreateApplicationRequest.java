package com.campus.lostfound.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建申请参数。申请类型由服务端根据帖子类型推导，因此请求体不接收 type。
 */
public record CreateApplicationRequest(
        @NotBlank(message = "申请说明不能为空")
        @Size(min = 5, max = 2000, message = "申请说明长度需为 5-2000 个字符")
        String description
) {
}
