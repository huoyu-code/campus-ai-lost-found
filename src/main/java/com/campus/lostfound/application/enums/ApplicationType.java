package com.campus.lostfound.application.enums;

import com.campus.lostfound.post.enums.PostType;

/**
 * 申请类型必须由帖子类型推导，客户端不能自行指定。
 *
 * <p>{@code FOUND -> CLAIM}，{@code LOST -> CLUE}。</p>
 */
public enum ApplicationType {
    CLAIM,
    CLUE;

    /**
     * 根据帖子类型得到唯一合法的申请类型。
     */
    public static ApplicationType forPostType(PostType postType) {
        return postType == PostType.FOUND ? CLAIM : CLUE;
    }

    public boolean matches(PostType postType) {
        return this == forPostType(postType);
    }
}
