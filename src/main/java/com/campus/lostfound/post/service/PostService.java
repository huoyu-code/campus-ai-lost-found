package com.campus.lostfound.post.service;

import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.post.dto.CreatePostRequest;
import com.campus.lostfound.post.dto.PostQuery;
import com.campus.lostfound.post.dto.UpdatePostRequest;
import com.campus.lostfound.post.dto.UpdatePostStatusRequest;
import com.campus.lostfound.post.vo.PostVO;

/**
 * 失物/招领帖子业务边界。
 */
public interface PostService {

    /**
     * 创建 {@code PUBLISHED} 帖子并保存图片 URL。
     */
    PostVO create(Long userId, CreatePostRequest request);

    /**
     * 按当前请求用户权限返回详情，必要时隐藏联系方式。
     */
    PostVO getDetail(Long postId);

    /**
     * 返回公开状态的分页帖子列表。
     */
    PageResult<PostVO> pagePublic(PostQuery query);

    /**
     * 返回当前用户发布的全部状态帖子。
     */
    PageResult<PostVO> pageMine(Long userId, PostQuery query);

    /**
     * 管理员查看全状态帖子，支持关键词和发布者筛选。
     */
    PageResult<PostVO> pageForAdmin(
            Long operatorId, PostQuery query, String keyword, Long publisherId);

    /**
     * 发布者编辑仍处于 {@code PUBLISHED} 的帖子。
     */
    PostVO update(Long postId, Long userId, UpdatePostRequest request);

    /**
     * 发布者关闭帖子或确认完成。
     */
    PostVO updateStatus(Long postId, Long userId, UpdatePostStatusRequest request);
}

