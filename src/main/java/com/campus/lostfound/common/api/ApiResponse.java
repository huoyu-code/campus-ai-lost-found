package com.campus.lostfound.common.api;

import java.time.Instant;

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

