package com.campus.lostfound.notification.enums;

/**
 * 持久化通知类型。
 *
 * <p>类型是稳定协议值，前端可据此选择图标或跳转目标；通知正文仍以 MySQL 数据为准。</p>
 */
public enum NotificationType {
    APPLICATION_RECEIVED,
    APPLICATION_ACCEPTED,
    APPLICATION_REJECTED,
    COMPLETION_REQUESTED,
    POST_RESOLVED,
    CHAT_MESSAGE
}
