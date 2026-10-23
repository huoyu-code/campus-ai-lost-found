package com.campus.lostfound.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.application.dto.ApplicationDecisionRequest;
import com.campus.lostfound.application.dto.ApplicationQuery;
import com.campus.lostfound.application.dto.CreateApplicationRequest;
import com.campus.lostfound.application.entity.ClaimApplication;
import com.campus.lostfound.application.enums.ApplicationDecision;
import com.campus.lostfound.application.enums.ApplicationStatus;
import com.campus.lostfound.application.enums.ApplicationType;
import com.campus.lostfound.application.mapper.ClaimApplicationMapper;
import com.campus.lostfound.application.service.ApplicationService;
import com.campus.lostfound.application.vo.ApplicationVO;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.post.entity.LostFoundPost;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.mapper.LostFoundPostMapper;
import com.campus.lostfound.post.vo.UserSummaryVO;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 申请业务实现。
 *
 * <p>申请写操作全部在事务中完成。接受申请时先条件更新帖子抢占处理权，
 * 再更新申请并拒绝其他待处理记录，任何一步失败都会回滚整个事务。</p>
 */
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private static final String OTHER_ACCEPTED_NOTE = "该帖其他申请已被接受";

    private final ClaimApplicationMapper applicationMapper;
    private final LostFoundPostMapper postMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO create(
            Long postId, Long applicantId, CreateApplicationRequest request) {
        LostFoundPost post = requirePost(postId);
        SysUser applicant = requireActiveUser(applicantId);
        // 发布者不能给自己的帖子制造申请和完成记录。
        if (Objects.equals(post.getPublisherId(), applicantId)) {
            throw ApiException.badRequest("不能申请自己发布的帖子");
        }
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw ApiException.conflict("当前帖子状态不接受新申请");
        }

        // 预查询用于返回明确提示；数据库唯一索引负责并发重复提交的最终保护。
        long activeCount = applicationMapper.selectCount(
                new LambdaQueryWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getPostId, postId)
                        .eq(ClaimApplication::getApplicantId, applicant.getId())
                        .in(ClaimApplication::getStatus,
                                ApplicationStatus.PENDING, ApplicationStatus.ACCEPTED));
        if (activeCount > 0) {
            throw ApiException.conflict("你已提交过该帖子的有效申请");
        }

        LocalDateTime now = LocalDateTime.now();
        ClaimApplication application = ClaimApplication.builder()
                .postId(postId)
                .applicantId(applicant.getId())
                .applicationType(ApplicationType.forPostType(post.getPostType()))
                .description(request.description().trim())
                .status(ApplicationStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException exception) {
            // 两个请求同时通过预查询时，后插入者会命中 uk_application_active。
            throw ApiException.conflict("你已提交过该帖子的有效申请");
        }
        return assemble(List.of(requireApplication(application.getId()))).getFirst();
    }

    @Override
    public PageResult<ApplicationVO> pageForPost(
            Long postId, Long operatorId, ApplicationQuery query) {
        LostFoundPost post = requirePost(postId);
        SysUser operator = requireActiveUser(operatorId);
        // 申请中可能包含联系方式证明，只有帖子发布者或管理员能查看整组申请。
        if (operator.getRole() != UserRole.ADMIN
                && !Objects.equals(post.getPublisherId(), operatorId)) {
            throw ApiException.forbidden("只有发布者或管理员可以查看申请");
        }
        return page(query, buildQuery(query).eq(ClaimApplication::getPostId, postId));
    }

    @Override
    public PageResult<ApplicationVO> pageMine(Long applicantId, ApplicationQuery query) {
        requireActiveUser(applicantId);
        return page(query, buildQuery(query)
                .eq(ClaimApplication::getApplicantId, applicantId));
    }

    @Override
    public PageResult<ApplicationVO> pageForAdmin(
            Long operatorId, ApplicationQuery query) {
        SysUser operator = requireActiveUser(operatorId);
        if (operator.getRole() != UserRole.ADMIN) {
            throw ApiException.forbidden("仅管理员可以查看全平台申请");
        }
        return page(query, buildQuery(query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO decide(
            Long applicationId, Long operatorId, ApplicationDecisionRequest request) {
        ClaimApplication application = requireApplication(applicationId);
        LostFoundPost post = requirePost(application.getPostId());
        SysUser operator = requireActiveUser(operatorId);
        // 先验证处理人身份，再暴露或修改申请状态。
        if (operator.getRole() != UserRole.ADMIN
                && !Objects.equals(post.getPublisherId(), operatorId)) {
            throw ApiException.forbidden("只有发布者或管理员可以处理申请");
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw ApiException.conflict("申请已处理，不能重复操作");
        }
        if (!application.getApplicationType().matches(post.getPostType())) {
            throw ApiException.conflict("申请类型与帖子类型不一致");
        }

        LocalDateTime now = LocalDateTime.now();
        String processNote = trimToNull(request.processNote());
        // 接受和拒绝走不同事务步骤：接受会联动帖子及其他申请。
        if (request.decision() == ApplicationDecision.ACCEPT) {
            accept(application, post, operator, processNote, now);
        } else {
            reject(application, operator, processNote, now);
        }
        return assemble(List.of(requireApplication(applicationId))).getFirst();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO withdraw(Long applicationId, Long applicantId) {
        ClaimApplication application = requireApplication(applicationId);
        // 撤回权限只属于申请人本人，管理员也不能代替申请人撤回历史。
        if (!Objects.equals(application.getApplicantId(), applicantId)) {
            throw ApiException.forbidden("只有申请人可以撤回申请");
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw ApiException.conflict("只有待处理申请可以撤回");
        }

        int updated = applicationMapper.update(null,
                new LambdaUpdateWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getId, applicationId)
                        .eq(ClaimApplication::getApplicantId, applicantId)
                        .eq(ClaimApplication::getStatus, ApplicationStatus.PENDING)
                        .set(ClaimApplication::getStatus, ApplicationStatus.WITHDRAWN)
                        .set(ClaimApplication::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            // WHERE status=PENDING 保证并发接受和撤回只有一个操作成功。
            throw ApiException.conflict("申请状态已变化，请刷新后重试");
        }
        return assemble(List.of(requireApplication(applicationId))).getFirst();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO requestCompletion(Long applicationId, Long applicantId) {
        ClaimApplication application = requireApplication(applicationId);
        if (!Objects.equals(application.getApplicantId(), applicantId)) {
            throw ApiException.forbidden("只有申请人可以发起完成请求");
        }
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            throw ApiException.conflict("只有已接受的申请可以发起完成请求");
        }
        LostFoundPost post = requirePost(application.getPostId());
        if (post.getStatus() != PostStatus.PROCESSING) {
            throw ApiException.conflict("帖子当前不是处理中状态");
        }
        if (application.getCompletionRequestedAt() != null) {
            // 重复点击视为幂等操作，直接返回已有完成请求时间。
            return assemble(List.of(application)).getFirst();
        }

        int updated = applicationMapper.update(null,
                new LambdaUpdateWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getId, applicationId)
                        .eq(ClaimApplication::getApplicantId, applicantId)
                        .eq(ClaimApplication::getStatus, ApplicationStatus.ACCEPTED)
                        .isNull(ClaimApplication::getCompletionRequestedAt)
                        .set(ClaimApplication::getCompletionRequestedAt,
                                LocalDateTime.now())
                        .set(ClaimApplication::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            // completion_requested_at IS NULL 防止并发请求写出不同的完成时间。
            throw ApiException.conflict("申请状态已变化，请刷新后重试");
        }
        return assemble(List.of(requireApplication(applicationId))).getFirst();
    }

    private void accept(
            ClaimApplication application,
            LostFoundPost post,
            SysUser operator,
            String processNote,
            LocalDateTime now) {
        // 第一步抢占帖子：只有从 PUBLISHED 成功移到 PROCESSING 的请求才能继续。
        int postUpdated = postMapper.update(null,
                new LambdaUpdateWrapper<LostFoundPost>()
                        .eq(LostFoundPost::getId, post.getId())
                        .eq(LostFoundPost::getStatus, PostStatus.PUBLISHED)
                        .set(LostFoundPost::getStatus, PostStatus.PROCESSING)
                        .set(LostFoundPost::getUpdatedAt, now));
        if (postUpdated != 1) {
            throw ApiException.conflict("帖子已被处理或当前状态不可接受申请");
        }

        int applicationUpdated;
        try {
            // 第二步条件更新申请；uk_application_one_accepted 约束单帖只能有一个接受者。
            applicationUpdated = applicationMapper.update(null,
                    new LambdaUpdateWrapper<ClaimApplication>()
                            .eq(ClaimApplication::getId, application.getId())
                            .eq(ClaimApplication::getStatus, ApplicationStatus.PENDING)
                            .set(ClaimApplication::getStatus, ApplicationStatus.ACCEPTED)
                            .set(ClaimApplication::getProcessedBy, operator.getId())
                            .set(ClaimApplication::getProcessNote, processNote)
                            .set(ClaimApplication::getProcessedAt, now)
                            .set(ClaimApplication::getUpdatedAt, now));
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("该帖子已有其他申请被接受");
        }
        if (applicationUpdated != 1) {
            throw ApiException.conflict("申请状态已变化，请刷新后重试");
        }

        // 第三步将同帖其他 PENDING 申请统一拒绝，已处理申请不受影响。
        applicationMapper.update(null,
                new LambdaUpdateWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getPostId, post.getId())
                        .eq(ClaimApplication::getStatus, ApplicationStatus.PENDING)
                        .ne(ClaimApplication::getId, application.getId())
                        .set(ClaimApplication::getStatus, ApplicationStatus.REJECTED)
                        .set(ClaimApplication::getProcessedBy, operator.getId())
                        .set(ClaimApplication::getProcessNote, OTHER_ACCEPTED_NOTE)
                        .set(ClaimApplication::getProcessedAt, now)
                        .set(ClaimApplication::getUpdatedAt, now));
    }

    private void reject(
            ClaimApplication application,
            SysUser operator,
            String processNote,
            LocalDateTime now) {
        // 拒绝只推进申请状态，不改变帖子；状态条件防止重复处理。
        int updated = applicationMapper.update(null,
                new LambdaUpdateWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getId, application.getId())
                        .eq(ClaimApplication::getStatus, ApplicationStatus.PENDING)
                        .set(ClaimApplication::getStatus, ApplicationStatus.REJECTED)
                        .set(ClaimApplication::getProcessedBy, operator.getId())
                        .set(ClaimApplication::getProcessNote, processNote)
                        .set(ClaimApplication::getProcessedAt, now)
                        .set(ClaimApplication::getUpdatedAt, now));
        if (updated != 1) {
            throw ApiException.conflict("申请状态已变化，请刷新后重试");
        }
    }

    private PageResult<ApplicationVO> page(
            ApplicationQuery query, LambdaQueryWrapper<ClaimApplication> wrapper) {
        wrapper.orderByDesc(ClaimApplication::getCreatedAt)
                .orderByDesc(ClaimApplication::getId);
        Page<ClaimApplication> page = applicationMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                assemble(page.getRecords()),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    private LambdaQueryWrapper<ClaimApplication> buildQuery(ApplicationQuery query) {
        return new LambdaQueryWrapper<ClaimApplication>()
                .eq(query.getStatus() != null,
                        ClaimApplication::getStatus, query.getStatus())
                .eq(query.getApplicationType() != null,
                        ClaimApplication::getApplicationType, query.getApplicationType())
                .eq(query.getPostId() != null,
                        ClaimApplication::getPostId, query.getPostId());
    }

    private List<ApplicationVO> assemble(List<ClaimApplication> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        // 批量加载帖子和用户，避免列表接口逐条查询。
        Set<Long> postIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (ClaimApplication application : applications) {
            postIds.add(application.getPostId());
            userIds.add(application.getApplicantId());
            if (application.getProcessedBy() != null) {
                userIds.add(application.getProcessedBy());
            }
        }

        Map<Long, LostFoundPost> posts = new HashMap<>();
        for (LostFoundPost post : postMapper.selectByIds(postIds)) {
            posts.put(post.getId(), post);
        }
        Map<Long, SysUser> users = new HashMap<>();
        for (SysUser user : userMapper.selectByIds(userIds)) {
            users.put(user.getId(), user);
        }

        List<ApplicationVO> result = new ArrayList<>(applications.size());
        for (ClaimApplication application : applications) {
            LostFoundPost post = posts.get(application.getPostId());
            result.add(new ApplicationVO(
                    application.getId(),
                    application.getPostId(),
                    post == null ? null : post.getTitle(),
                    post == null ? null : post.getPostType(),
                    post == null ? null : post.getStatus(),
                    application.getApplicationType(),
                    application.getDescription(),
                    application.getStatus(),
                    summary(users.get(application.getApplicantId())),
                    summary(users.get(application.getProcessedBy())),
                    application.getProcessNote(),
                    application.getProcessedAt(),
                    application.getCompletionRequestedAt(),
                    application.getCreatedAt(),
                    application.getUpdatedAt()
            ));
        }
        return result;
    }

    private UserSummaryVO summary(SysUser user) {
        return user == null ? null
                : new UserSummaryVO(user.getId(), user.getNickname(), user.getAvatarUrl());
    }

    private ClaimApplication requireApplication(Long applicationId) {
        ClaimApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw ApiException.notFound("申请不存在");
        }
        return application;
    }

    private LostFoundPost requirePost(Long postId) {
        LostFoundPost post = postMapper.selectById(postId);
        if (post == null) {
            throw ApiException.notFound("帖子不存在");
        }
        return post;
    }

    private SysUser requireActiveUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("登录用户不存在或已被禁用");
        }
        return user;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
