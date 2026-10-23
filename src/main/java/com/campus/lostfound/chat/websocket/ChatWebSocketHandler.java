package com.campus.lostfound.chat.websocket;

import com.campus.lostfound.chat.dto.SendChatMessagePayload;
import com.campus.lostfound.chat.service.ChatService;
import com.campus.lostfound.chat.service.MessageSendResult;
import com.campus.lostfound.common.api.ApiCode;
import com.campus.lostfound.common.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 聊天 WebSocket 入口。
 *
 * <p>连接只证明用户身份；每条消息仍由 ChatService 查询会话参与关系。收到消息后
 * 先持久化，再返回 ACK 并向在线接收者推送，内存 Session 不是消息数据源。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ChatService chatService;
    private final ChatSessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        sessionRegistry.register(userId, session);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session, TextMessage message) {
        Long userId = userId(session);
        String requestId = null;
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            requestId = extractRequestId(root);
            String type = requiredText(root, "type");
            switch (type) {
                case "PING" -> handlePing(session, requestId);
                case "CHAT_MESSAGE" -> handleChatMessage(
                        session, userId, requestId, root.path("data"));
                default -> throw ApiException.badRequest("不支持的 WebSocket 消息类型");
            }
        } catch (ApiException exception) {
            sendError(session, requestId, exception.getCode(), exception.getMessage());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            sendError(session, requestId, ApiCode.VALIDATION_ERROR,
                    "WebSocket 消息格式不正确");
        } catch (Exception exception) {
            // 不记录消息正文或完整异常消息，避免私聊内容进入日志。
            log.error("Unexpected WebSocket error, sessionId={}, userId={}, error={}",
                    session.getId(), userId, exception.getClass().getSimpleName());
            sendError(session, requestId, ApiCode.INTERNAL_ERROR,
                    "服务器内部错误");
        }
    }

    private void handlePing(WebSocketSession session, String requestId) {
        String effectiveRequestId = validateRequestId(requestId);
        sessionRegistry.sendToSession(
                session.getId(),
                new WebSocketEvent<>(
                        WebSocketEventType.PONG,
                        effectiveRequestId,
                        new PongData(Instant.now()))
        );
    }

    private void handleChatMessage(
            WebSocketSession session,
            Long userId,
            String requestId,
            JsonNode dataNode) throws JsonProcessingException {
        String effectiveRequestId = validateRequestId(requestId);
        SendChatMessagePayload payload =
                objectMapper.treeToValue(dataNode, SendChatMessagePayload.class);
        validatePayload(payload);

        MessageSendResult result = chatService.sendMessage(
                payload.conversationId(),
                userId,
                effectiveRequestId,
                payload.content()
        );
        ChatMessageAckStatus status = result.duplicate()
                ? ChatMessageAckStatus.DUPLICATE
                : ChatMessageAckStatus.SENT;
        sessionRegistry.sendToSession(
                session.getId(),
                new WebSocketEvent<>(
                        WebSocketEventType.CHAT_MESSAGE_ACK,
                        effectiveRequestId,
                        new ChatMessageAck(status, result.message()))
        );

        // 新消息由 ChatService 在事务提交后推送给对端；这里额外同步发送者其他设备。
        if (!result.duplicate()) {
            sessionRegistry.sendToUserExcept(
                    userId,
                    session.getId(),
                    new WebSocketEvent<>(
                            WebSocketEventType.CHAT_MESSAGE,
                            effectiveRequestId,
                            result.message())
            );
        }
    }

    private void validatePayload(SendChatMessagePayload payload) {
        Set<ConstraintViolation<SendChatMessagePayload>> violations =
                validator.validate(payload);
        if (!violations.isEmpty()) {
            throw ApiException.badRequest(
                    violations.iterator().next().getMessage());
        }
    }

    private String extractRequestId(JsonNode root) {
        JsonNode node = root.get("requestId");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.badRequest("requestId 必须是字符串");
        }
        String requestId = node.asText();
        if (requestId.length() > 64) {
            throw ApiException.badRequest("requestId 不能超过 64 个字符");
        }
        return requestId;
    }

    private String validateRequestId(String requestId) {
        if (requestId == null || !REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            throw ApiException.badRequest(
                    "requestId 只能包含字母、数字、下划线和短横线，且不超过 64 个字符");
        }
        return requestId;
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw ApiException.badRequest(field + " 不能为空");
        }
        return node.asText();
    }

    private void sendError(
            WebSocketSession session, String requestId, ApiCode code, String message) {
        sessionRegistry.sendToSession(
                session.getId(),
                new WebSocketEvent<>(
                        WebSocketEventType.ERROR,
                        safeRequestId(requestId),
                        new WebSocketErrorData(code, message))
        );
    }

    private String safeRequestId(String requestId) {
        return requestId != null && requestId.length() <= 64 ? requestId : null;
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes()
                .get(ChatTicketHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (!(value instanceof Long userId)) {
            throw ApiException.unauthorized("WebSocket 连接未认证");
        }
        return userId;
    }

    @Override
    public void handleTransportError(
            WebSocketSession session, Throwable exception) {
        // Session 只影响在线推送，已入库消息仍可通过 REST 查询。
        log.debug("WebSocket transport error, sessionId={}, error={}",
                session.getId(), exception.getClass().getSimpleName());
        sessionRegistry.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session.getId());
    }
}
