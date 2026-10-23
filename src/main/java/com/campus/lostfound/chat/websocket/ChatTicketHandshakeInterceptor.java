package com.campus.lostfound.chat.websocket;

import com.campus.lostfound.auth.service.WebSocketTicketPrincipal;
import com.campus.lostfound.auth.service.WebSocketTicketService;
import com.campus.lostfound.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手认证拦截器。
 *
 * <p>票据只放在握手 URL 查询参数中，且为 30 秒一次性凭证；长期 JWT 不进入 URL。</p>
 */
@Component
@RequiredArgsConstructor
public class ChatTicketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "chatUserId";

    private final WebSocketTicketService ticketService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        if (!StringUtils.hasText(ticket)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            WebSocketTicketPrincipal principal = ticketService.consume(ticket);
            attributes.put(USER_ID_ATTRIBUTE, principal.userId());
            return true;
        } catch (ApiException exception) {
            // 握手阶段不写入票据内容或认证快照，只返回对应 HTTP 状态。
            response.setStatusCode(exception.getStatus());
            return false;
        } catch (RuntimeException exception) {
            // 数据库或依赖发生未预期故障时拒绝连接，不把异常详情写入握手响应。
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // 无额外清理；票据已在 beforeHandshake 中原子消费。
    }
}
