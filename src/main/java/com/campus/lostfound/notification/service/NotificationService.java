package com.campus.lostfound.notification.service;

import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.notification.dto.NotificationQuery;
import com.campus.lostfound.notification.enums.NotificationType;
import com.campus.lostfound.notification.vo.NotificationReadResult;
import com.campus.lostfound.notification.vo.NotificationVO;
import com.campus.lostfound.notification.vo.UnreadCountVO;

/**
 * 站内通知业务边界。
 */
public interface NotificationService {

    /**
     * 持久化一条通知，并在事务提交后尝试实时推送。
     */
    NotificationVO create(
            Long userId,
            NotificationType type,
            String title,
            String content,
            String businessType,
            Long businessId);

    PageResult<NotificationVO> pageMine(Long userId, NotificationQuery query);

    UnreadCountVO unreadCount(Long userId);

    NotificationVO markRead(Long notificationId, Long userId);

    NotificationReadResult markAllRead(Long userId);
}
