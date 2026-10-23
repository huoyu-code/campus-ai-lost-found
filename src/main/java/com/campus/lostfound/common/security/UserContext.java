package com.campus.lostfound.common.security;

import com.campus.lostfound.common.exception.ApiException;

public final class UserContext {

    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        CURRENT.set(user);
    }

    public static LoginUser get() {
        return CURRENT.get();
    }

    public static LoginUser require() {
        LoginUser user = CURRENT.get();
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return user;
    }

    public static Long requireUserId() {
        return require().userId();
    }

    public static void requireAdmin() {
        if (!require().isAdmin()) {
            throw ApiException.forbidden("仅管理员可执行该操作");
        }
    }

    public static void clear() {
        CURRENT.remove();
    }
}

