package com.campus.lostfound.user.service;

import com.campus.lostfound.user.dto.ChangePasswordRequest;
import com.campus.lostfound.user.dto.UpdateProfileRequest;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.vo.UserProfileVO;

public interface UserService {

    SysUser requireUser(Long userId);

    UserProfileVO getProfile(Long userId);

    UserProfileVO updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    UserProfileVO toProfile(SysUser user);
}

