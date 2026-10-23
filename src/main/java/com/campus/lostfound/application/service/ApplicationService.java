package com.campus.lostfound.application.service;

import com.campus.lostfound.application.dto.ApplicationDecisionRequest;
import com.campus.lostfound.application.dto.ApplicationQuery;
import com.campus.lostfound.application.dto.CreateApplicationRequest;
import com.campus.lostfound.application.vo.ApplicationVO;
import com.campus.lostfound.common.api.PageResult;

/**
 * 认领/线索申请业务边界。
 */
public interface ApplicationService {

    /**
     * 对他人 {@code PUBLISHED} 帖子创建申请，类型由帖子类型自动推导。
     */
    ApplicationVO create(Long postId, Long applicantId, CreateApplicationRequest request);

    /**
     * 发布者或管理员分页查看某帖子的申请。
     */
    PageResult<ApplicationVO> pageForPost(
            Long postId, Long operatorId, ApplicationQuery query);

    /**
     * 查看当前用户提交的申请历史。
     */
    PageResult<ApplicationVO> pageMine(Long applicantId, ApplicationQuery query);

    /**
     * 管理员分页查看全平台申请。
     */
    PageResult<ApplicationVO> pageForAdmin(Long operatorId, ApplicationQuery query);

    /**
     * 发布者或管理员接受/拒绝待处理申请。
     */
    ApplicationVO decide(
            Long applicationId, Long operatorId, ApplicationDecisionRequest request);

    /**
     * 申请人撤回自己的待处理申请。
     */
    ApplicationVO withdraw(Long applicationId, Long applicantId);

    /**
     * 已接受申请人发起完成请求，后续由发布者确认帖子完成。
     */
    ApplicationVO requestCompletion(Long applicationId, Long applicantId);
}
