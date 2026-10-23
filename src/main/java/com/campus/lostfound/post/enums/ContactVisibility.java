package com.campus.lostfound.post.enums;

/**
 * 帖子联系方式的可见范围，由 Service 在组装响应时强制裁剪。
 */
public enum ContactVisibility {
    /** 任意已登录用户可见。 */
    AUTHENTICATED,

    /** 仅发布者、管理员和被接受的申请人可见。 */
    ACCEPTED_ONLY,

    /** 仅发布者和管理员可见。 */
    OWNER_ONLY
}

