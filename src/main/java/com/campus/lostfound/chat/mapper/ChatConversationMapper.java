package com.campus.lostfound.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话表数据访问入口。
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
