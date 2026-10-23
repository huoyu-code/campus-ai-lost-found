package com.campus.lostfound.chat.vo;

import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;
import com.campus.lostfound.post.vo.UserSummaryVO;

import java.time.LocalDateTime;

/**
 * 会话列表视图，包含帖子摘要、对端用户和最后消息时间。
 */
public record ChatConversationVO(
        Long id,
        Long postId,
        String postTitle,
        PostType postType,
        PostStatus postStatus,
        UserSummaryVO peer,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {
}
