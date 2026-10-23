package com.campus.lostfound.chat;

import com.campus.lostfound.auth.service.TokenRevocationService;
import com.campus.lostfound.auth.service.WebSocketTicketPrincipal;
import com.campus.lostfound.auth.service.WebSocketTicketService;
import com.campus.lostfound.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

/**
 * 真实嵌入式 WebSocket 聊天链路测试。
 *
 * <p>测试覆盖票据握手入口、消息 envelope、持久化 ACK、PING/PONG、幂等重试、
 * REST 离线历史和接收方通知。票据服务被替换为确定性测试桩，真实 Redis
 * 一次性消费会在后续 Windows 环境联调中单独验证。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @MockitoBean
    private WebSocketTicketService ticketService;

    @Test
    void persistsMessageAcknowledgesIdempotencyAndSupportsOfflineHistory()
            throws Exception {
        Session owner = registerAndLogin("ws_owner");
        Session applicant = registerAndLogin("ws_user");
        Session outsider = registerAndLogin("ws_other");
        long postId = createPost(owner);
        createApplication(applicant, postId);
        long conversationId = createConversation(owner, postId, applicant.userId());

        String ticket = "ws_test_" + UUID.randomUUID().toString().replace("-", "");
        when(ticketService.consume(ticket))
                .thenReturn(new WebSocketTicketPrincipal(applicant.userId()));

        EventListener listener = new EventListener();
        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://127.0.0.1:" + port
                                + "/api/ws/chat?ticket=" + ticket),
                        listener)
                .get(5, TimeUnit.SECONDS);
        try {
            webSocket.sendText("""
                    {"type":"PING","requestId":"ping_1"}
                    """, true).get(5, TimeUnit.SECONDS);
            JsonNode pong = objectMapper.readTree(listener.next(5));
            assertThat(pong.path("type").asText()).isEqualTo("PONG");
            assertThat(pong.path("requestId").asText()).isEqualTo("ping_1");

            String requestId = "ws_" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 16);
            String payload = """
                    {
                      "type": "CHAT_MESSAGE",
                      "requestId": "%s",
                      "data": {
                        "conversationId": %d,
                        "content": "这是通过 WebSocket 发送的消息"
                      }
                    }
                    """.formatted(requestId, conversationId);
            webSocket.sendText(payload, true).get(5, TimeUnit.SECONDS);

            JsonNode ack = objectMapper.readTree(listener.next(5));
            assertThat(ack.path("type").asText()).isEqualTo("CHAT_MESSAGE_ACK");
            assertThat(ack.path("requestId").asText()).isEqualTo(requestId);
            assertThat(ack.path("data").path("status").asText()).isEqualTo("SENT");
            long messageId = ack.path("data").path("message").path("id").asLong();
            assertThat(messageId).isPositive();

            // 重复发送同一 envelope 不会产生第二条消息，ACK 明确标记为 DUPLICATE。
            webSocket.sendText(payload, true).get(5, TimeUnit.SECONDS);
            JsonNode duplicateAck = objectMapper.readTree(listener.next(5));
            assertThat(duplicateAck.path("data").path("status").asText())
                    .isEqualTo("DUPLICATE");
            assertThat(duplicateAck.path("data").path("message").path("id").asLong())
                    .isEqualTo(messageId);

            // 已登录但不在会话中的用户即使绕过前端，也不能通过 WS 写入消息。
            String outsiderTicket = "ws_test_" + UUID.randomUUID().toString()
                    .replace("-", "");
            when(ticketService.consume(outsiderTicket))
                    .thenReturn(new WebSocketTicketPrincipal(outsider.userId()));
            EventListener outsiderListener = new EventListener();
            WebSocket outsiderSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(
                            URI.create("ws://127.0.0.1:" + port
                                    + "/api/ws/chat?ticket=" + outsiderTicket),
                            outsiderListener)
                    .get(5, TimeUnit.SECONDS);
            try {
                outsiderSocket.sendText("""
                        {
                          "type": "CHAT_MESSAGE",
                          "requestId": "outsider_1",
                          "data": {
                            "conversationId": %d,
                            "content": "越权消息"
                          }
                        }
                        """.formatted(conversationId), true).get(5, TimeUnit.SECONDS);
                JsonNode denied = objectMapper.readTree(outsiderListener.next(5));
                assertThat(denied.path("type").asText()).isEqualTo("ERROR");
                assertThat(denied.path("data").path("code").asText())
                        .isEqualTo("NOT_FOUND");
            } finally {
                outsiderSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete")
                        .get(5, TimeUnit.SECONDS);
            }

            // 即使 WebSocket 断开或客户端重连，消息仍能从 MySQL 通过 REST 拉取。
            JsonNode history = exchange(
                    applicant,
                    HttpMethod.GET,
                    "/conversations/" + conversationId + "/messages",
                    null,
                    200
            );
            assertThat(history.path("data").path("total").asLong()).isEqualTo(1);
            assertThat(history.path("data").path("records").path(0).path("id").asLong())
                    .isEqualTo(messageId);

            JsonNode notifications = exchange(
                    owner,
                    HttpMethod.GET,
                    "/notifications?type=CHAT_MESSAGE",
                    null,
                    200
            );
            assertThat(notifications.path("data").path("total").asLong()).isEqualTo(1);
            assertThat(notifications.path("data").path("records")
                    .path(0).path("businessId").asLong()).isEqualTo(conversationId);
        } finally {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete")
                    .get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsInvalidTicketDuringHandshake() {
        when(ticketService.consume("invalid_ticket"))
                .thenThrow(ApiException.unauthorized("WebSocket 票据无效"));

        Throwable failure = catchThrowable(() -> HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://127.0.0.1:" + port
                                + "/api/ws/chat?ticket=invalid_ticket"),
                        new EventListener())
                .join());
        assertThat(failure)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(java.net.http.WebSocketHandshakeException.class);
        var handshake = (java.net.http.WebSocketHandshakeException) failure.getCause();
        assertThat(handshake.getResponse().statusCode()).isEqualTo(401);
    }

    private long createConversation(Session owner, long postId, long peerUserId)
            throws Exception {
        JsonNode response = exchange(
                owner,
                HttpMethod.POST,
                "/conversations",
                """
                        {"postId": %d, "peerUserId": %d}
                        """.formatted(postId, peerUserId),
                201
        );
        return response.path("data").path("id").asLong();
    }

    private void createApplication(Session applicant, long postId) throws Exception {
        exchange(
                applicant,
                HttpMethod.POST,
                "/posts/" + postId + "/applications",
                "{\"description\":\"我可以描述物品细节和拾取经过\"}",
                201
        );
    }

    private long createPost(Session owner) throws Exception {
        JsonNode response = exchange(
                owner,
                HttpMethod.POST,
                "/posts",
                """
                        {
                          "postType": "FOUND",
                          "title": "WebSocket 联调校园卡",
                          "description": "用于真实嵌入式 WebSocket 测试",
                          "category": "证件卡片",
                          "location": "图书馆",
                          "occurrenceTime": "2026-10-23T10:30:00",
                          "contactText": "值班室领取",
                          "contactVisibility": "ACCEPTED_ONLY",
                          "imageUrls": []
                        }
                        """,
                201
        );
        return response.path("data").path("id").asLong();
    }

    private Session registerAndLogin(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = prefix.substring(0, Math.min(prefix.length(), 8))
                + "_" + suffix;
        String password = "Campus2026";
        JsonNode registered = post(
                "/auth/register",
                """
                        {
                          "username": "%s",
                          "password": "%s",
                          "nickname": "%s"
                        }
                        """.formatted(username, password, prefix),
                201,
                null
        );
        JsonNode login = post(
                "/auth/login",
                """
                        {
                          "username": "%s",
                          "password": "%s"
                        }
                        """.formatted(username, password),
                200,
                null
        );
        assertThat(registered.path("data").path("id").asLong()).isPositive();
        return new Session(
                "Bearer " + login.path("data").path("accessToken").asText(),
                login.path("data").path("user").path("id").asLong()
        );
    }

    private JsonNode post(String path, String body, int expectedStatus, Session session)
            throws Exception {
        return exchange(session, HttpMethod.POST, path, body, expectedStatus);
    }

    private JsonNode exchange(
            Session session,
            HttpMethod method,
            String path,
            String body,
            int expectedStatus) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (session != null) {
            headers.set(HttpHeaders.AUTHORIZATION, session.authorization());
        }
        var response = restTemplate.exchange(
                "http://127.0.0.1:" + port + "/api" + path,
                method,
                new HttpEntity<>(body, headers),
                String.class
        );
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        return objectMapper.readTree(response.getBody());
    }

    private record Session(String authorization, long userId) {
    }

    /**
     * Java HttpClient 的文本帧可能分片到达，需要在最后一帧拼成完整 JSON。
     */
    private static final class EventListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<?> onText(
                WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        String next(long seconds) throws InterruptedException {
            String message = messages.poll(seconds, TimeUnit.SECONDS);
            if (message == null) {
                throw new IllegalStateException("Timed out waiting for WebSocket message");
            }
            return message;
        }
    }
}
