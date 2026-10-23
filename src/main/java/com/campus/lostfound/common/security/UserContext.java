package com.campus.lostfound.common.security;

import com.campus.lostfound.common.exception.ApiException;

/**
 * 当前请求的登录用户上下文。
 *
 * <p>身份由 {@code JwtAuthenticationFilter} 写入，在请求结束后必须清理。
 * Service 可以读取该上下文做数据可见性判断，但接口授权仍由拦截器和具体业务规则共同保证。</p>
 */
public final class UserContext {

    // 每个请求线程独立保存身份，避免通过 HTTP Session 传递状态。
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

