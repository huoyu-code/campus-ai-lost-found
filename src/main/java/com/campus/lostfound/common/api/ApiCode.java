package com.campus.lostfound.common.api;

/**
 * 稳定的业务响应码，供前端区分错误类型。
 *
 * <p>HTTP 状态码表达协议层结果，这里的枚举表达业务层结果；两者由全局异常处理器保持一致。</p>
 */
public enum ApiCode {
    SUCCESS,
    VALIDATION_ERROR,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    TOO_MANY_REQUESTS,
    SERVICE_UNAVAILABLE,
    AI_UPSTREAM_ERROR,
    INTERNAL_ERROR
}

