package com.campus.lostfound.post.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.common.trace.TraceIds;
import com.campus.lostfound.post.dto.PostQuery;
import com.campus.lostfound.post.service.PostService;
import com.campus.lostfound.post.vo.PostVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/posts")
@RequiredArgsConstructor
public class MyPostController {

    private final PostService postService;

    @GetMapping
    public ApiResponse<PageResult<PostVO>> page(
            @Valid @ModelAttribute PostQuery query,
            HttpServletRequest request) {
        return ApiResponse.success(
                postService.pageMine(UserContext.requireUserId(), query),
                TraceIds.get(request)
        );
    }
}

