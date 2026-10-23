package com.campus.lostfound.post.vo;

/**
 * 帖子或申请中展示给其他用户的公开用户摘要，只包含昵称和头像。
 */
public record UserSummaryVO(
        Long id,
        String nickname,
        String avatarUrl
) {
}

