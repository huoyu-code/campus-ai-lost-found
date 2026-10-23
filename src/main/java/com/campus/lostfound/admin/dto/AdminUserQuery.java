package com.campus.lostfound.admin.dto;

import com.campus.lostfound.common.api.PageQuery;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员用户列表筛选条件。
 */
@Getter
@Setter
public class AdminUserQuery extends PageQuery {

    private String keyword;

    private UserRole role;

    private UserStatus status;
}
