package com.campus.lostfound.chat.service;

import com.campus.lostfound.chat.dto.CreateConversationRequest;
import com.campus.lostfound.chat.vo.ChatConversationVO;
import com.campus.lostfound.chat.vo.ChatMessageVO;
import com.campus.lostfound.common.api.PageQuery;
import com.campus.lostfound.common.api.PageResult;

/**
 * 双人聊天会话和持久化消息业务边界。
 */
public interface ChatService {

    ConversationCreationResult createConversation(
            Long currentUserId, CreateConversationRequest request);

    PageResult<ChatConversationVO> pageConversations(Long userId, PageQuery query);

    PageResult<ChatMessageVO> pageMessages(
            Long conversationId, Long userId, PageQuery query);

    MessageSendResult sendMessage(
            Long conversationId,
            Long senderId,
            String requestId,
            String content);
}
