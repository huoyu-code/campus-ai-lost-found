package com.campus.lostfound.auth.controller;

import com.campus.lostfound.auth.service.AuthService;
import com.campus.lostfound.auth.vo.LoginResponse;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import com.campus.lostfound.user.dto.LoginRequest;
import com.campus.lostfound.user.dto.RegisterRequest;
import com.campus.lostfound.user.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册、登录和退出接口。
 *
 * <p>Controller 只负责 HTTP 参数与状态码，密码校验、令牌签发和吊销都在 Service 中完成。</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileVO>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                authService.register(request), TraceIds.get(servletRequest)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                authService.login(request), TraceIds.get(servletRequest));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest servletRequest) {
        // 注销只吊销当前令牌的 jti，不影响同一账号在其他设备上的会话。
        authService.logout(UserContext.require());
        return ApiResponse.success(null, TraceIds.get(servletRequest));
    }
}

