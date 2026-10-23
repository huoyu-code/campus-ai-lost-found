package com.campus.lostfound.application.controller;

import com.campus.lostfound.application.dto.ApplicationDecisionRequest;
import com.campus.lostfound.application.dto.ApplicationQuery;
import com.campus.lostfound.application.dto.CreateApplicationRequest;
import com.campus.lostfound.application.service.ApplicationService;
import com.campus.lostfound.application.vo.ApplicationVO;
import com.campus.lostfound.common.api.ApiResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 申请创建、查询、处理和完成请求接口。
 *
 * <p>所有接口都要求登录。发布者/管理员权限以及申请人权限在 Service 中校验，
 * 不能只依赖前端是否显示按钮。</p>
 */
@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping("/posts/{postId}/applications")
    public ResponseEntity<ApiResponse<ApplicationVO>> create(
            @PathVariable Long postId,
            @Valid @RequestBody CreateApplicationRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                applicationService.create(postId, UserContext.requireUserId(), body),
                TraceIds.get(request)
        ));
    }

    @GetMapping("/posts/{postId}/applications")
    public ApiResponse<PageResult<ApplicationVO>> pageForPost(
            @PathVariable Long postId,
            @Valid @ModelAttribute ApplicationQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                applicationService.pageForPost(
                        postId, UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @GetMapping("/users/me/applications")
    public ApiResponse<PageResult<ApplicationVO>> pageMine(
            @Valid @ModelAttribute ApplicationQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                applicationService.pageMine(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/applications/{id}/decision")
    public ApiResponse<ApplicationVO> decide(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationDecisionRequest body,
            HttpServletRequest request) {
        // 接受操作会在事务内同时修改帖子和其他申请，具体规则见 ApplicationService。
        return ApiResponse.success(
                applicationService.decide(id, UserContext.requireUserId(), body),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/applications/{id}/withdraw")
    public ApiResponse<ApplicationVO> withdraw(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ApiResponse.success(
                applicationService.withdraw(id, UserContext.requireUserId()),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/applications/{id}/completion-request")
    public ApiResponse<ApplicationVO> requestCompletion(
            @PathVariable Long id,
            HttpServletRequest request) {
        return ApiResponse.success(
                applicationService.requestCompletion(id, UserContext.requireUserId()),
                TraceIds.get(request)
        );
    }
}
