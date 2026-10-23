package com.campus.lostfound.admin.controller;

import com.campus.lostfound.admin.dto.AdminPostQuery;
import com.campus.lostfound.admin.dto.AdminUserQuery;
import com.campus.lostfound.admin.dto.ModeratePostRequest;
import com.campus.lostfound.admin.dto.OperationLogQuery;
import com.campus.lostfound.admin.dto.UpdateUserStatusRequest;
import com.campus.lostfound.admin.service.AdminService;
import com.campus.lostfound.admin.vo.AdminStatisticsVO;
import com.campus.lostfound.admin.vo.AdminUserVO;
import com.campus.lostfound.admin.vo.OperationLogVO;
import com.campus.lostfound.application.dto.ApplicationQuery;
import com.campus.lostfound.application.vo.ApplicationVO;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import com.campus.lostfound.post.vo.PostVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员后台接口。
 *
 * <p>所有方法先执行角色检查，再由 Service 查询数据库再次确认操作人仍是启用状态的管理员。</p>
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ApiResponse<PageResult<AdminUserVO>> pageUsers(
            @Valid @ModelAttribute AdminUserQuery query,
            HttpServletRequest request) {
        UserContext.requireAdmin();
        return ApiResponse.success(
                adminService.pageUsers(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserVO> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest body,
            HttpServletRequest request) {
        UserContext.requireAdmin();
        return ApiResponse.success(
                adminService.updateUserStatus(UserContext.requireUserId(), id, body),
                TraceIds.get(request)
        );
    }

    @GetMapping("/posts")
    public ApiResponse<PageResult<PostVO>> pagePosts(
            @Valid @ModelAttribute AdminPostQuery query,
            HttpServletRequest request) {
        UserContext.requireAdmin();
        return ApiResponse.success(
                adminService.pagePosts(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/posts/{id}/moderation")
    public ApiResponse<PostVO> moderatePost(
            @PathVariable Long id,
            @Valid @RequestBody ModeratePostRequest body,
            HttpServletRequest request) {
        UserContext.requireAdmin();
        return ApiResponse.success(
                adminService.moderatePost(UserContext.requireUserId(), id, body),
                TraceIds.get(request)
        );
    }

    @GetMapping("/applications")
    public ApiResponse<PageResult<ApplicationVO>> pageApplications(
            @Valid @ModelAttribute ApplicationQuery query,
            HttpServletRequest request) {
        UserContext.requireAdmin();
        return ApiResponse.success(
                adminService.pageApplications(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @GetMapping("/statistics")
    public ApiResponse<AdminStatisticsVO> statistics(HttpServletRequest request) {
        UserContext.requireAdmin();
        return ApiResponse.success(
                adminService.statistics(UserContext.requireUserId()),
                TraceIds.get(request)
        );
    }

    @GetMapping("/operation-logs")
    public ApiResponse<PageResult<OperationLogVO>> pageOperationLogs(
            @Valid @ModelAttribute OperationLogQuery query,
            HttpServletRequest request) {
        UserContext.requireAdmin();
        return ApiResponse.success(
                adminService.pageOperationLogs(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }
}
