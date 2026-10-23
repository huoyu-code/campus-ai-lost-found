package com.campus.lostfound.post.vo;

import com.campus.lostfound.post.enums.ContactVisibility;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;

import java.time.LocalDateTime;
import java.util.List;

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

