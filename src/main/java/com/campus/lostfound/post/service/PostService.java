package com.campus.lostfound.post.service;

import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.post.dto.CreatePostRequest;
import com.campus.lostfound.post.dto.PostQuery;
import com.campus.lostfound.post.dto.UpdatePostRequest;
import com.campus.lostfound.post.dto.UpdatePostStatusRequest;
import com.campus.lostfound.post.vo.PostVO;

public interface PostService {

    PostVO create(Long userId, CreatePostRequest request);

    PostVO getDetail(Long postId);

    PageResult<PostVO> pagePublic(PostQuery query);

    PageResult<PostVO> pageMine(Long userId, PostQuery query);

    PostVO update(Long postId, Long userId, UpdatePostRequest request);

    PostVO updateStatus(Long postId, Long userId, UpdatePostStatusRequest request);
}

