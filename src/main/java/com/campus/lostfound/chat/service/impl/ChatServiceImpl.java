package com.campus.lostfound.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.application.entity.ClaimApplication;
import com.campus.lostfound.application.mapper.ClaimApplicationMapper;
import com.campus.lostfound.chat.dto.CreateConversationRequest;
import com.campus.lostfound.chat.entity.ChatConversation;
import com.campus.lostfound.chat.entity.ChatMessage;
import com.campus.lostfound.chat.mapper.ChatConversationMapper;
import com.campus.lostfound.chat.mapper.ChatMessageMapper;
import com.campus.lostfound.chat.service.ChatService;
import com.campus.lostfound.chat.service.ConversationCreationResult;
import com.campus.lostfound.chat.service.MessageSendResult;
import com.campus.lostfound.chat.vo.ChatConversationVO;
import com.campus.lostfound.chat.vo.ChatMessageVO;
import com.campus.lostfound.chat.websocket.ChatSessionRegistry;
import com.campus.lostfound.chat.websocket.WebSocketEvent;
import com.campus.lostfound.chat.websocket.WebSocketEventType;
import com.campus.lostfound.common.api.PageQuery;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.notification.enums.NotificationType;
import com.campus.lostfound.notification.service.NotificationService;
import com.campus.lostfound.post.entity.LostFoundPost;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.mapper.LostFoundPostMapper;
import com.campus.lostfound.post.vo.UserSummaryVO;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 聊天业务实现。
 *
 * <p>会话参与关系、消息内容、幂等标识和帖子状态都在 Service 中重新校验。
 * 消息、会话最后消息时间和接收方通知处于同一个数据库事务，提交后才进行实时推送。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final String BUSINESS_CONVERSATION = "CONVERSATION";

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final LostFoundPostMapper postMapper;
    private final ClaimApplicationMapper applicationMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final ChatSessionRegistry chatSessionRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationCreationResult createConversation(
            Long currentUserId, CreateConversationRequest request) {
        SysUser current = requireActiveUser(currentUserId);
        SysUser peer = requireActiveUser(request.peerUserId());
        if (Objects.equals(current.getId(), peer.getId())) {
            throw ApiException.badRequest("不能与自己创建聊天会话");
        }
        LostFoundPost post = requirePost(request.postId());
        long lowId = Math.min(current.getId(), peer.getId());
        long highId = Math.max(current.getId(), peer.getId());

        ChatConversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<ChatConversation>()
                        .eq(ChatConversation::getPostId, post.getId())
                        .eq(ChatConversation::getUserLowId, lowId)
                        .eq(ChatConversation::getUserHighId, highId));
        if (existing != null) {
            // 同一个“帖子 + 用户对”重复创建时返回已有会话，避免前端重复点击产生冲突。
            return new ConversationCreationResult(
                    assembleConversations(List.of(existing), currentUserId).getFirst(), false);
        }

        boolean currentIsPublisher = Objects.equals(post.getPublisherId(), currentUserId);
        boolean peerIsPublisher = Objects.equals(post.getPublisherId(), peer.getId());
        if (currentIsPublisher == peerIsPublisher) {
            throw ApiException.forbidden("会话双方必须分别是帖子发布者和申请人");
        }
        if (post.getStatus() != PostStatus.PUBLISHED
                && post.getStatus() != PostStatus.PROCESSING) {
            throw ApiException.conflict("当前帖子状态不能创建新会话");
        }

        Long applicantId = currentIsPublisher ? peer.getId() : current.getId();
        long applicationCount = applicationMapper.selectCount(
                new LambdaQueryWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getPostId, post.getId())
                        .eq(ClaimApplication::getApplicantId, applicantId));
        if (applicationCount == 0) {
            throw ApiException.forbidden("只有该帖发布者和申请人可以创建会话");
        }

        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = ChatConversation.builder()
                .postId(post.getId())
                .userLowId(lowId)
                .userHighId(highId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException exception) {
            // 并发创建命中唯一索引时，返回已经由另一个请求创建的会话。
            ChatConversation concurrent = conversationMapper.selectOne(
                    new LambdaQueryWrapper<ChatConversation>()
                            .eq(ChatConversation::getPostId, post.getId())
                            .eq(ChatConversation::getUserLowId, lowId)
                            .eq(ChatConversation::getUserHighId, highId));
            if (concurrent == null) {
                throw exception;
            }
            return new ConversationCreationResult(
                    assembleConversations(List.of(concurrent), currentUserId).getFirst(), false);
        }
        return new ConversationCreationResult(
                assembleConversations(List.of(conversation), currentUserId).getFirst(), true);
    }

    @Override
    public PageResult<ChatConversationVO> pageConversations(
            Long userId, PageQuery query) {
        requireActiveUser(userId);
        LambdaQueryWrapper<ChatConversation> wrapper =
                new LambdaQueryWrapper<ChatConversation>()
                        .and(nested -> nested
                                .eq(ChatConversation::getUserLowId, userId)
                                .or()
                                .eq(ChatConversation::getUserHighId, userId))
                        .orderByDesc(ChatConversation::getLastMessageAt)
                        .orderByDesc(ChatConversation::getCreatedAt)
                        .orderByDesc(ChatConversation::getId);
        Page<ChatConversation> page = conversationMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                assembleConversations(page.getRecords(), userId),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    @Override
    public PageResult<ChatMessageVO> pageMessages(
            Long conversationId, Long userId, PageQuery query) {
        requireActiveUser(userId);
        requireParticipant(requireConversation(conversationId), userId);
        Page<ChatMessage> page = messageMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId));
        return new PageResult<>(
                assembleMessages(page.getRecords()),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageSendResult sendMessage(
            Long conversationId,
            Long senderId,
            String requestId,
            String content) {
        String normalizedRequestId = validateRequestId(requestId);
        String normalizedContent = validateContent(content);
        SysUser sender = requireActiveUser(senderId);
        ChatConversation conversation = requireConversation(conversationId);
        requireParticipant(conversation, senderId);
        LostFoundPost post = requirePost(conversation.getPostId());
        if (post.getStatus() == PostStatus.HIDDEN) {
            throw ApiException.conflict("帖子已被隐藏，不能继续发送消息");
        }

        ChatMessage existing = messageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSenderId, senderId)
                        .eq(ChatMessage::getRequestId, normalizedRequestId));
        if (existing != null) {
            return duplicateResult(existing, conversationId, normalizedContent);
        }

        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .requestId(normalizedRequestId)
                .content(normalizedContent)
                .createdAt(now)
                .build();
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException exception) {
            // 数据库唯一索引负责并发重试的最终幂等保护。
            throw ApiException.conflict("该 requestId 已被使用，请重新获取消息状态");
        }

        conversationMapper.update(null,
                new LambdaUpdateWrapper<ChatConversation>()
                        .eq(ChatConversation::getId, conversationId)
                        .set(ChatConversation::getLastMessageAt, now)
                        .set(ChatConversation::getUpdatedAt, now));

        Long recipientId = Objects.equals(conversation.getUserLowId(), senderId)
                ? conversation.getUserHighId() : conversation.getUserLowId();
        notificationService.create(
                recipientId,
                NotificationType.CHAT_MESSAGE,
                "收到新的聊天消息",
                sender.getNickname() + " 发来一条聊天消息",
                BUSINESS_CONVERSATION,
                conversationId
        );

        ChatMessageVO messageVO = assembleMessages(List.of(message)).getFirst();
        publishChatAfterCommit(recipientId, messageVO);
        return new MessageSendResult(messageVO, false);
    }

    private MessageSendResult duplicateResult(
            ChatMessage existing, Long conversationId, String content) {
        if (!Objects.equals(existing.getConversationId(), conversationId)
                || !Objects.equals(existing.getContent(), content)) {
            throw ApiException.conflict("该 requestId 已用于其他消息内容");
        }
        return new MessageSendResult(
                assembleMessages(List.of(existing)).getFirst(), true);
    }

    private List<ChatConversationVO> assembleConversations(
            List<ChatConversation> conversations, Long currentUserId) {
        if (conversations.isEmpty()) {
            return List.of();
        }
        Set<Long> postIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (ChatConversation conversation : conversations) {
            postIds.add(conversation.getPostId());
            userIds.add(conversation.getUserLowId());
            userIds.add(conversation.getUserHighId());
        }

        Map<Long, LostFoundPost> posts = new HashMap<>();
        for (LostFoundPost post : postMapper.selectByIds(postIds)) {
            posts.put(post.getId(), post);
        }
        Map<Long, SysUser> users = new HashMap<>();
        for (SysUser user : userMapper.selectByIds(userIds)) {
            users.put(user.getId(), user);
        }

        List<ChatConversationVO> result = new ArrayList<>(conversations.size());
        for (ChatConversation conversation : conversations) {
            LostFoundPost post = posts.get(conversation.getPostId());
            Long peerId = Objects.equals(conversation.getUserLowId(), currentUserId)
                    ? conversation.getUserHighId() : conversation.getUserLowId();
            result.add(new ChatConversationVO(
                    conversation.getId(),
                    conversation.getPostId(),
                    post == null ? null : post.getTitle(),
                    post == null ? null : post.getPostType(),
                    post == null ? null : post.getStatus(),
                    summary(users.get(peerId)),
                    conversation.getLastMessageAt(),
                    conversation.getCreatedAt()
            ));
        }
        return result;
    }

    private List<ChatMessageVO> assembleMessages(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Set<Long> senderIds = messages.stream()
                .map(ChatMessage::getSenderId)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        Map<Long, SysUser> senders = new HashMap<>();
        for (SysUser user : userMapper.selectByIds(senderIds)) {
            senders.put(user.getId(), user);
        }
        return messages.stream()
                .map(message -> new ChatMessageVO(
                        message.getId(),
                        message.getConversationId(),
                        message.getRequestId(),
                        summary(senders.get(message.getSenderId())),
                        message.getContent(),
                        message.getCreatedAt()))
                .toList();
    }

    private void publishChatAfterCommit(Long recipientId, ChatMessageVO message) {
        Runnable publish = () -> chatSessionRegistry.sendToUser(
                recipientId,
                new WebSocketEvent<>(WebSocketEventType.CHAT_MESSAGE, null, message)
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

    private String validateRequestId(String requestId) {
        String normalized = requestId == null ? "" : requestId.trim();
        if (!REQUEST_ID_PATTERN.matcher(normalized).matches()) {
            throw ApiException.badRequest(
                    "requestId 只能包含字母、数字、下划线和短横线，且不超过 64 个字符");
        }
        return normalized;
    }

    private String validateContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("消息内容不能为空");
        }
        if (normalized.length() > 2000) {
            throw ApiException.badRequest("消息内容不能超过 2000 个字符");
        }
        return normalized;
    }

    private ChatConversation requireConversation(Long conversationId) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw ApiException.notFound("会话不存在");
        }
        return conversation;
    }

    private LostFoundPost requirePost(Long postId) {
        LostFoundPost post = postMapper.selectById(postId);
        if (post == null) {
            throw ApiException.notFound("帖子不存在");
        }
        return post;
    }

    private SysUser requireActiveUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("登录用户不存在或已被禁用");
        }
        return user;
    }

    private void requireParticipant(ChatConversation conversation, Long userId) {
        if (!Objects.equals(conversation.getUserLowId(), userId)
                && !Objects.equals(conversation.getUserHighId(), userId)) {
            // 不区分“会话不存在”和“无权访问”，避免泄露其他用户会话 ID。
            throw ApiException.notFound("会话不存在");
        }
    }

    private UserSummaryVO summary(SysUser user) {
        return user == null ? null
                : new UserSummaryVO(user.getId(), user.getNickname(), user.getAvatarUrl());
    }
}
