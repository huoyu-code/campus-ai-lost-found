package com.campus.lostfound.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息表数据访问入口。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
