package com.campus.lostfound.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.lostfound.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知实体，对应 {@code user_notification}。
 *
 * <p>{@code businessType + businessId} 是应用层解释的多态业务引用，不建立物理外键。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_notification")
public class UserNotification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private NotificationType notificationType;

    private String title;

    private String content;

    private String businessType;

    private Long businessId;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
