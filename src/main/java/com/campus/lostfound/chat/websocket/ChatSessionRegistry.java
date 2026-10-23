package com.campus.lostfound.chat.websocket;

import com.campus.lostfound.auth.config.WebSocketProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例在线 WebSocket 连接注册表。
 *
 * <p>这里只保存当前 JVM 中的在线连接，不保存消息正文。数据库才是消息和通知的
 * 持久化来源；多实例部署时需要额外的广播机制或共享在线状态方案。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionRegistry {

    private final ObjectMapper objectMapper;
    private final WebSocketProperties properties;

    private final ConcurrentHashMap<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sessionUsers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> userSessions =
            new ConcurrentHashMap<>();

    /**
     * 注册连接时使用并发装饰器，避免多个业务线程同时向同一连接发送时破坏帧顺序。
     */
    public void register(Long userId, WebSocketSession rawSession) {
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(
                rawSession,
                properties.sendTimeLimitMillis(),
                properties.sendBufferSizeBytes()
        );
        sessions.put(rawSession.getId(), session);
        sessionUsers.put(rawSession.getId(), userId);
        userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(rawSession.getId());
    }

    public void remove(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        Long userId = sessionUsers.remove(sessionId);
        if (userId != null) {
            Set<String> ids = userSessions.get(userId);
            if (ids != null) {
                ids.remove(sessionId);
                if (ids.isEmpty()) {
                    userSessions.remove(userId, ids);
                }
            }
        }
        closeQuietly(session);
    }

    public boolean sendToSession(String sessionId, WebSocketEvent<?> event) {
        WebSocketSession session = sessions.get(sessionId);
        return send(session, event);
    }

    public void sendToUser(Long userId, WebSocketEvent<?> event) {
        Set<String> ids = userSessions.get(userId);
        if (ids == null) {
            return;
        }
        for (String sessionId : Set.copyOf(ids)) {
            sendToSession(sessionId, event);
        }
    }

    public void sendToUserExcept(
            Long userId, String excludedSessionId, WebSocketEvent<?> event) {
        Set<String> ids = userSessions.get(userId);
        if (ids == null) {
            return;
        }
        for (String sessionId : Set.copyOf(ids)) {
            if (!sessionId.equals(excludedSessionId)) {
                sendToSession(sessionId, event);
            }
        }
    }

    /**
     * 用户被禁用后关闭其全部在线连接；消息持久化数据不受影响。
     */
    public void closeUser(Long userId) {
        Set<String> ids = userSessions.get(userId);
        if (ids == null) {
            return;
        }
        for (String sessionId : Set.copyOf(ids)) {
            remove(sessionId);
        }
    }

    private boolean send(WebSocketSession session, WebSocketEvent<?> event) {
        if (session == null || !session.isOpen()) {
            if (session != null) {
                remove(session.getId());
            }
            return false;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
            return true;
        } catch (IOException | IllegalStateException exception) {
            // 实时推送失败不回滚已经提交的消息；客户端可随后通过 REST 拉取历史消息。
            log.warn("WebSocket push failed, sessionId={}, error={}",
                    session.getId(), exception.getClass().getSimpleName());
            remove(session.getId());
            return false;
        }
    }

    private void closeQuietly(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(CloseStatus.NORMAL);
        } catch (IOException exception) {
            log.debug("WebSocket close failed, sessionId={}, error={}",
                    session.getId(), exception.getClass().getSimpleName());
        }
    }
}
