package com.campus.lostfound.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.notification.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知表数据访问入口。
 */
@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {
}
