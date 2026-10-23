package com.campus.lostfound.user.controller;

import com.campus.lostfound.auth.service.TokenRevocationService;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import com.campus.lostfound.user.dto.ChangePasswordRequest;
import com.campus.lostfound.user.dto.UpdateProfileRequest;
import com.campus.lostfound.user.service.UserService;
import com.campus.lostfound.user.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TokenRevocationService tokenRevocationService;

    @GetMapping("/me")
    public ApiResponse<UserProfileVO> me(HttpServletRequest request) {
        return ApiResponse.success(
                userService.getProfile(UserContext.requireUserId()),
                TraceIds.get(request));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileVO> update(
            @Valid @RequestBody UpdateProfileRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(
                userService.updateProfile(UserContext.requireUserId(), body),
                TraceIds.get(request));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request) {
        LoginUser currentUser = UserContext.require();
        userService.changePassword(currentUser.userId(), body);
        tokenRevocationService.revoke(currentUser);
        return ApiResponse.success(null, TraceIds.get(request));
    }
}

