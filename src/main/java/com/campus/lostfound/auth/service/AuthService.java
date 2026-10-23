package com.campus.lostfound.auth.service;

import com.campus.lostfound.auth.vo.LoginResponse;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.user.dto.LoginRequest;
import com.campus.lostfound.user.dto.RegisterRequest;
import com.campus.lostfound.user.vo.UserProfileVO;

/**
 * 认证业务边界。
 */
public interface AuthService {

    /**
     * 创建普通用户账号并返回脱敏后的用户资料。
     */
    UserProfileVO register(RegisterRequest request);

    /**
     * 校验密码和账号状态，更新最后登录时间并签发 JWT。
     */
    LoginResponse login(LoginRequest request);

    /**
     * 将当前令牌加入 Redis 吊销集合。
     */
    void logout(LoginUser user);
}

