package com.campus.lostfound.search.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.trace.TraceIds;
import com.campus.lostfound.post.service.PostService;
import com.campus.lostfound.post.vo.PostVO;
import com.campus.lostfound.search.dto.PostSearchQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开帖子搜索接口。
 *
 * <p>搜索只访问 MySQL，不依赖 Redis、搜索引擎或大模型。匿名用户可以调用，
 * 但 Service 会强制过滤普通用户不可见的帖子状态。</p>
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final PostService postService;

    @GetMapping("/posts")
    public ApiResponse<PageResult<PostVO>> searchPosts(
            @Valid @ModelAttribute PostSearchQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                postService.searchPublic(query),
                TraceIds.get(request)
        );
    }
}
