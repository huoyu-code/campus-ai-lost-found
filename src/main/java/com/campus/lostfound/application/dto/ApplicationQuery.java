package com.campus.lostfound.application.dto;

import com.campus.lostfound.application.enums.ApplicationStatus;
import com.campus.lostfound.application.enums.ApplicationType;
import com.campus.lostfound.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 申请列表的筛选条件，可按状态和帖子过滤。
 */
@Getter
@Setter
public class ApplicationQuery extends PageQuery {

    private ApplicationStatus status;

    private ApplicationType applicationType;

    private Long postId;
}
