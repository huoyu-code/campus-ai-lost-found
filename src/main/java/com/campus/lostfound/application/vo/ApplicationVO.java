package com.campus.lostfound.application.vo;

import com.campus.lostfound.application.enums.ApplicationStatus;
import com.campus.lostfound.application.enums.ApplicationType;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;
import com.campus.lostfound.post.vo.UserSummaryVO;

import java.time.LocalDateTime;

/**
 * 申请对外视图，同时携带帖子摘要和申请人摘要，便于前端直接渲染列表。
 */
public record ApplicationVO(
        Long id,
        Long postId,
        String postTitle,
        PostType postType,
        PostStatus postStatus,
        ApplicationType applicationType,
        String description,
        ApplicationStatus status,
        UserSummaryVO applicant,
        UserSummaryVO processedBy,
        String processNote,
        LocalDateTime processedAt,
        LocalDateTime completionRequestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
