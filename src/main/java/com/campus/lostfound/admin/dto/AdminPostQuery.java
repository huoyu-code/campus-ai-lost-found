package com.campus.lostfound.admin.dto;

import com.campus.lostfound.common.api.PageQuery;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员帖子列表筛选条件。
 */
@Getter
@Setter
public class AdminPostQuery extends PageQuery {

    private String keyword;

    private PostType postType;

    private PostStatus status;

    private Long publisherId;
}
