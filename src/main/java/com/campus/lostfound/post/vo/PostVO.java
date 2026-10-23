package com.campus.lostfound.post.vo;

import com.campus.lostfound.post.enums.ContactVisibility;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子对外视图。
 *
 * <p>列表响应不返回完整描述；{@code contactText} 是否为空由服务端根据可见范围决定，
 * 不能依赖前端隐藏敏感字段。</p>
 */
public record PostVO(
        Long id,
        PostType postType,
        String title,
        String description,
        String category,
        String location,
        LocalDateTime occurrenceTime,
        String contactText,
        ContactVisibility contactVisibility,
        PostStatus status,
        List<String> imageUrls,
        String coverImage,
        UserSummaryVO publisher,
        boolean owner,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

