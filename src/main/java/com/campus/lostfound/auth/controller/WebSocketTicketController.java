package com.campus.lostfound.auth.controller;

import com.campus.lostfound.auth.service.WebSocketTicketService;
import com.campus.lostfound.auth.vo.WebSocketTicketVO;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 为当前 JWT 用户签发一次性 WebSocket 握手票据。
 */
@RestController
@RequestMapping("/ws-ticket")
@RequiredArgsConstructor
public class WebSocketTicketController {

    private final WebSocketTicketService ticketService;

    @PostMapping
    public ApiResponse<WebSocketTicketVO> create(HttpServletRequest request) {
        return ApiResponse.success(
                ticketService.create(UserContext.require()),
                TraceIds.get(request)
        );
    }
}
