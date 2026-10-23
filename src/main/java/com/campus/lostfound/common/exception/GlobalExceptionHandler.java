package com.campus.lostfound.common.exception;

import com.campus.lostfound.common.api.ApiCode;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.trace.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常到 HTTP 响应的一致性映射。
 *
 * <p>参数错误、认证失败、权限不足、资源不存在和状态冲突分别使用对应 HTTP 状态码。
 * 未识别异常只返回通用提示和 traceId，详细堆栈仅写入服务端日志。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException exception, HttpServletRequest request) {
        return failure(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("；"));
        return failure(HttpStatus.BAD_REQUEST, ApiCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("；"));
        return failure(HttpStatus.BAD_REQUEST, ApiCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("；"));
        return failure(HttpStatus.BAD_REQUEST, ApiCode.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            Exception exception, HttpServletRequest request) {
        return failure(HttpStatus.BAD_REQUEST, ApiCode.VALIDATION_ERROR, "请求参数格式不正确", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return failure(HttpStatus.METHOD_NOT_ALLOWED, ApiCode.VALIDATION_ERROR,
                "不支持当前请求方式", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return failure(HttpStatus.NOT_FOUND, ApiCode.NOT_FOUND, "接口不存在", request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(
            DuplicateKeyException exception, HttpServletRequest request) {
        // 唯一索引是并发写入时的最后一道保护，例如重复申请或重复用户名。
        return failure(HttpStatus.CONFLICT, ApiCode.CONFLICT, "数据已存在，请勿重复提交", request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrencyFailure(
            ConcurrencyFailureException exception, HttpServletRequest request) {
        return failure(HttpStatus.CONFLICT, ApiCode.CONFLICT,
                "数据正在被其他操作修改，请刷新后重试", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String traceId = TraceIds.get(request);
        log.error("Unhandled exception, traceId={}, path={}", traceId,
                request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        ApiCode.INTERNAL_ERROR, "服务器内部错误，请联系管理员", traceId));
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            HttpStatus status, ApiCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(code, message, TraceIds.get(request)));
    }
}

