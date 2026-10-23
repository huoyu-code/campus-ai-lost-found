package com.campus.lostfound.common.exception;

import com.campus.lostfound.common.api.ApiCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 可预期的业务异常。
 *
 * <p>Service 抛出该异常表示请求本身不合法、无权限或存在状态冲突，
 * 由全局异常处理器转换为统一响应；堆栈不会返回给客户端。</p>
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiCode code;

    public ApiException(HttpStatus status, ApiCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiCode.VALIDATION_ERROR, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ApiCode.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ApiCode.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ApiCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ApiCode.CONFLICT, message);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, ApiCode.SERVICE_UNAVAILABLE, message);
    }
}

