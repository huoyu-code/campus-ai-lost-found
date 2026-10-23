package com.campus.lostfound.notification.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import com.campus.lostfound.notification.dto.NotificationQuery;
import com.campus.lostfound.notification.service.NotificationService;
import com.campus.lostfound.notification.vo.NotificationReadResult;
import com.campus.lostfound.notification.vo.NotificationVO;
import com.campus.lostfound.notification.vo.UnreadCountVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的持久化通知接口。
 *
 * <p>所有查询和已读操作都只作用于 JWT 对应用户，客户端不能指定通知所属用户。</p>
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResult<NotificationVO>> page(
            @Valid @ModelAttribute NotificationQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                notificationService.pageMine(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountVO> unreadCount(HttpServletRequest request) {
        return ApiResponse.success(
                notificationService.unreadCount(UserContext.requireUserId()),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationVO> markRead(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ApiResponse.success(
                notificationService.markRead(id, UserContext.requireUserId()),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/read-all")
    public ApiResponse<NotificationReadResult> markAllRead(
            HttpServletRequest request) {
        return ApiResponse.success(
                notificationService.markAllRead(UserContext.requireUserId()),
                TraceIds.get(request)
        );
    }
}
