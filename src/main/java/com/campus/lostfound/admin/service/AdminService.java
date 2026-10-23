package com.campus.lostfound.admin.service;

import com.campus.lostfound.admin.dto.AdminPostQuery;
import com.campus.lostfound.admin.dto.AdminUserQuery;
import com.campus.lostfound.admin.dto.ModeratePostRequest;
import com.campus.lostfound.admin.dto.OperationLogQuery;
import com.campus.lostfound.admin.dto.UpdateUserStatusRequest;
import com.campus.lostfound.admin.vo.AdminStatisticsVO;
import com.campus.lostfound.admin.vo.AdminUserVO;
import com.campus.lostfound.admin.vo.OperationLogVO;
import com.campus.lostfound.application.dto.ApplicationQuery;
import com.campus.lostfound.application.vo.ApplicationVO;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.post.vo.PostVO;

/**
 * 管理员后台业务边界。
 */
public interface AdminService {

    PageResult<AdminUserVO> pageUsers(Long operatorId, AdminUserQuery query);

    AdminUserVO updateUserStatus(
            Long operatorId, Long userId, UpdateUserStatusRequest request);

    PageResult<PostVO> pagePosts(Long operatorId, AdminPostQuery query);

    PostVO moderatePost(
            Long operatorId, Long postId, ModeratePostRequest request);

    PageResult<ApplicationVO> pageApplications(
            Long operatorId, ApplicationQuery query);

    AdminStatisticsVO statistics(Long operatorId);

    PageResult<OperationLogVO> pageOperationLogs(
            Long operatorId, OperationLogQuery query);
}
