package com.campus.lostfound.user.service;

import com.campus.lostfound.user.dto.ChangePasswordRequest;
import com.campus.lostfound.user.dto.UpdateProfileRequest;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.vo.UserProfileVO;

/**
 * 用户资料业务边界。
 */
public interface UserService {

    /**
     * 查询用户实体，供认证或其他业务模块做逻辑外键校验。
     */
    SysUser requireUser(Long userId);

    /**
     * 返回不包含密码哈希的用户资料。
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 对非空字段执行部分更新。
     */
    UserProfileVO updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 校验原密码后更新 BCrypt 哈希。
     */
    void changePassword(Long userId, ChangePasswordRequest request);

    /**
     * 将数据库实体转换为对外展示对象。
     */
    UserProfileVO toProfile(SysUser user);
}

