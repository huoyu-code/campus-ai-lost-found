package com.campus.lostfound.common.api;

import java.time.Instant;

/**
 * 所有普通接口共用的响应结构。
 *
 * @param code 业务结果类型
 * @param message 可直接展示给用户的说明
 * @param data 成功时的数据，失败时为 {@code null}
 * @param traceId 用于关联服务端日志的请求标识
 * @param timestamp 服务端生成响应的时间
 */
public record ApiResponse<T>(
        ApiCode code,
        String message,
        T data,
        String traceId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(ApiCode.SUCCESS, "操作成功", data, traceId, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ApiCode code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, Instant.now());
    }
}

