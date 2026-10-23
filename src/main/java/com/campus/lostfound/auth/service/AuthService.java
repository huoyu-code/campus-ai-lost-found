package com.campus.lostfound.auth.service;

import com.campus.lostfound.auth.vo.LoginResponse;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.user.dto.LoginRequest;
import com.campus.lostfound.user.dto.RegisterRequest;
import com.campus.lostfound.user.vo.UserProfileVO;

public interface AuthService {

    UserProfileVO register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(LoginUser user);
}

