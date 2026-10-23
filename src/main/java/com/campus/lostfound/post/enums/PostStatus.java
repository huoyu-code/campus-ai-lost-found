package com.campus.lostfound.post.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 帖子状态机。
 *
 * <pre>
 * PUBLISHED -> PROCESSING -> RESOLVED
 *      |            |
 *      +-------> CLOSED
 * HIDDEN 由管理员操作，不参与普通发布流程
 * </pre>
 */
public enum PostStatus {
    PUBLISHED,
    PROCESSING,
    RESOLVED,
    CLOSED,
    HIDDEN;

    /**
     * 游客在列表和详情中可以看见的状态。
     */
    public static Set<PostStatus> publiclyVisible() {
        return EnumSet.of(PUBLISHED, PROCESSING, RESOLVED);
    }

    public boolean isPubliclyVisible() {
        return publiclyVisible().contains(this);
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}

