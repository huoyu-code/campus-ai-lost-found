package com.campus.lostfound.chat.controller;

import com.campus.lostfound.chat.dto.CreateConversationRequest;
import com.campus.lostfound.chat.dto.SendMessageRequest;
import com.campus.lostfound.chat.service.ChatService;
import com.campus.lostfound.chat.service.ConversationCreationResult;
import com.campus.lostfound.chat.service.MessageSendResult;
import com.campus.lostfound.chat.vo.ChatConversationVO;
import com.campus.lostfound.chat.vo.ChatMessageVO;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageQuery;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 双人会话和历史消息 REST 接口。
 *
 * <p>REST 发送作为 WebSocket 不可用时的可用通道；两种入口共用同一个事务性
 * {@code ChatService.sendMessage}，因此幂等和权限规则完全一致。</p>
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatConversationVO>> create(
            @Valid @RequestBody CreateConversationRequest body,
            HttpServletRequest request) {
        ConversationCreationResult result = chatService.createConversation(
                UserContext.requireUserId(), body);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(
                result.conversation(), TraceIds.get(request)));
    }

    @GetMapping
    public ApiResponse<PageResult<ChatConversationVO>> page(
            @Valid @ModelAttribute PageQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                chatService.pageConversations(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<PageResult<ChatMessageVO>> messages(
            @PathVariable Long id,
            @Valid @ModelAttribute PageQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                chatService.pageMessages(id, UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<ChatMessageVO>> send(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest body,
            HttpServletRequest request) {
        MessageSendResult result = chatService.sendMessage(
                id,
                UserContext.requireUserId(),
                body.requestId(),
                body.content());
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success(
                result.message(), TraceIds.get(request)));
    }
}
