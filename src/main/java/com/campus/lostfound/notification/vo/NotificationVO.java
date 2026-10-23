package com.campus.lostfound.notification.vo;

import com.campus.lostfound.notification.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * 通知对外视图。
 */
public record NotificationVO(
        Long id,
        NotificationType type,
        String title,
        String content,
        String businessType,
        Long businessId,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
