package com.campus.lostfound.notification.dto;

import com.campus.lostfound.common.api.PageQuery;
import com.campus.lostfound.notification.enums.NotificationType;
import lombok.Getter;
import lombok.Setter;

/**
 * 当前用户通知列表筛选条件。
 */
@Getter
@Setter
public class NotificationQuery extends PageQuery {

    private Boolean unreadOnly;

    private NotificationType type;
}
