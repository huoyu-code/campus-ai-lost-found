package com.campus.lostfound.post.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import com.campus.lostfound.post.dto.CreatePostRequest;
import com.campus.lostfound.post.dto.PostQuery;
import com.campus.lostfound.post.dto.UpdatePostRequest;
import com.campus.lostfound.post.dto.UpdatePostStatusRequest;
import com.campus.lostfound.post.service.PostService;
import com.campus.lostfound.post.vo.PostVO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子的公开查询和登录用户写操作接口。
 *
 * <p>GET 列表与详情允许匿名访问；发布、编辑和状态变更要求登录，Service 再校验所有权。</p>
 */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostVO>> create(
            @Valid @RequestBody CreatePostRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                postService.create(UserContext.requireUserId(), body),
                TraceIds.get(request)
        ));
    }

    @GetMapping
    public ApiResponse<PageResult<PostVO>> page(
            @Valid @ModelAttribute PostQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                postService.pagePublic(query), TraceIds.get(request));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostVO> detail(
            @PathVariable Long postId,
            HttpServletRequest request) {
        return ApiResponse.success(
                postService.getDetail(postId), TraceIds.get(request));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostVO> update(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(
                postService.update(postId, UserContext.requireUserId(), body),
                TraceIds.get(request)
        );
    }

    @PatchMapping("/{postId}/status")
    public ApiResponse<PostVO> updateStatus(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostStatusRequest body,
            HttpServletRequest request) {
        // 当前只允许 CLOSED 和 RESOLVED，且 RESOLVED 必须先有完成请求。
        return ApiResponse.success(
                postService.updateStatus(postId, UserContext.requireUserId(), body),
                TraceIds.get(request)
        );
    }
}

