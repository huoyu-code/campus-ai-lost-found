package com.campus.lostfound.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.chat.websocket.ChatSessionRegistry;
import com.campus.lostfound.chat.websocket.WebSocketEvent;
import com.campus.lostfound.chat.websocket.WebSocketEventType;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.notification.dto.NotificationQuery;
import com.campus.lostfound.notification.entity.UserNotification;
import com.campus.lostfound.notification.enums.NotificationType;
import com.campus.lostfound.notification.mapper.UserNotificationMapper;
import com.campus.lostfound.notification.service.NotificationService;
import com.campus.lostfound.notification.vo.NotificationReadResult;
import com.campus.lostfound.notification.vo.NotificationVO;
import com.campus.lostfound.notification.vo.UnreadCountVO;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知实现。
 *
 * <p>通知正文先写 MySQL，实时 WebSocket 只负责提醒。事务内创建的通知必须等到提交后
 * 才推送，避免数据库回滚而客户端已经收到不存在的数据。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final UserNotificationMapper notificationMapper;
    private final SysUserMapper userMapper;
    private final ChatSessionRegistry chatSessionRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO create(
            Long userId,
            NotificationType type,
            String title,
            String content,
            String businessType,
            Long businessId) {
        SysUser recipient = userMapper.selectById(userId);
        if (recipient == null) {
            throw new IllegalStateException("通知接收用户不存在");
        }

        UserNotification notification = UserNotification.builder()
                .userId(userId)
                .notificationType(type)
                .title(title)
                .content(content)
                .businessType(businessType)
                .businessId(businessId)
                .createdAt(LocalDateTime.now())
                .build();
        notificationMapper.insert(notification);
        NotificationVO result = toVO(notification);
        publishAfterCommit(userId, result);
        return result;
    }

    @Override
    public PageResult<NotificationVO> pageMine(Long userId, NotificationQuery query) {
        requireActiveUser(userId);
        LambdaQueryWrapper<UserNotification> wrapper =
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(query.getType() != null,
                                UserNotification::getNotificationType, query.getType())
                        .isNull(Boolean.TRUE.equals(query.getUnreadOnly()),
                                UserNotification::getReadAt)
                        .orderByDesc(UserNotification::getCreatedAt)
                        .orderByDesc(UserNotification::getId);
        Page<UserNotification> page = notificationMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toVO).toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    @Override
    public UnreadCountVO unreadCount(Long userId) {
        requireActiveUser(userId);
        long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .isNull(UserNotification::getReadAt));
        return new UnreadCountVO(count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO markRead(Long notificationId, Long userId) {
        requireActiveUser(userId);
        UserNotification notification = notificationMapper.selectOne(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getId, notificationId)
                        .eq(UserNotification::getUserId, userId));
        if (notification == null) {
            // 统一返回 404，避免通过通知 ID 推断其他用户的数据是否存在。
            throw ApiException.notFound("通知不存在");
        }
        if (notification.getReadAt() == null) {
            LocalDateTime now = LocalDateTime.now();
            notificationMapper.update(null,
                    new LambdaUpdateWrapper<UserNotification>()
                            .eq(UserNotification::getId, notificationId)
                            .eq(UserNotification::getUserId, userId)
                            .isNull(UserNotification::getReadAt)
                            .set(UserNotification::getReadAt, now));
            notification.setReadAt(now);
        }
        return toVO(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationReadResult markAllRead(Long userId) {
        requireActiveUser(userId);
        int updated = notificationMapper.update(null,
                new LambdaUpdateWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .isNull(UserNotification::getReadAt)
                        .set(UserNotification::getReadAt, LocalDateTime.now()));
        return new NotificationReadResult(updated);
    }

    private void publishAfterCommit(Long userId, NotificationVO notification) {
        Runnable publish = () -> chatSessionRegistry.sendToUser(
                userId,
                new WebSocketEvent<>(WebSocketEventType.NOTIFICATION, null, notification)
        );
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish.run();
                        }
                    });
        } else {
            publish.run();
        }
    }

    private SysUser requireActiveUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("登录用户不存在或已被禁用");
        }
        return user;
    }

    private NotificationVO toVO(UserNotification notification) {
        return new NotificationVO(
                notification.getId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getBusinessType(),
                notification.getBusinessId(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
