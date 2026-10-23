package com.campus.lostfound.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.admin.dto.AdminPostQuery;
import com.campus.lostfound.admin.dto.AdminUserQuery;
import com.campus.lostfound.admin.dto.ModeratePostRequest;
import com.campus.lostfound.admin.dto.OperationLogQuery;
import com.campus.lostfound.admin.dto.UpdateUserStatusRequest;
import com.campus.lostfound.admin.entity.SysOperationLog;
import com.campus.lostfound.admin.enums.OperationResult;
import com.campus.lostfound.admin.enums.PostModerationAction;
import com.campus.lostfound.admin.mapper.SysOperationLogMapper;
import com.campus.lostfound.admin.service.AdminService;
import com.campus.lostfound.admin.service.OperationLogService;
import com.campus.lostfound.admin.vo.AdminStatisticsVO;
import com.campus.lostfound.admin.vo.AdminUserVO;
import com.campus.lostfound.admin.vo.OperationLogVO;
import com.campus.lostfound.application.dto.ApplicationQuery;
import com.campus.lostfound.application.entity.ClaimApplication;
import com.campus.lostfound.application.enums.ApplicationStatus;
import com.campus.lostfound.application.mapper.ClaimApplicationMapper;
import com.campus.lostfound.application.service.ApplicationService;
import com.campus.lostfound.application.vo.ApplicationVO;
import com.campus.lostfound.auth.service.TokenRevocationService;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.post.dto.PostQuery;
import com.campus.lostfound.post.entity.LostFoundPost;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.mapper.LostFoundPostMapper;
import com.campus.lostfound.post.service.PostService;
import com.campus.lostfound.post.vo.PostVO;
import com.campus.lostfound.post.vo.UserSummaryVO;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 管理员后台业务实现。
 *
 * <p>每次管理写操作都再次查询操作人角色，避免只信任 JWT 中的历史角色。
 * 成功状态变更与成功审计日志在同一事务提交；失败操作通过独立事务记录原因。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final String TARGET_USER = "USER";
    private static final String TARGET_POST = "POST";
    private static final String ACTION_USER_STATUS = "USER_STATUS_CHANGE";
    private static final String ACTION_POST_HIDE = "POST_HIDE";
    private static final String ACTION_POST_RESTORE = "POST_RESTORE";
    private static final Set<PostStatus> HIDEABLE_STATUSES =
            EnumSet.of(PostStatus.PUBLISHED, PostStatus.PROCESSING, PostStatus.RESOLVED);

    private final SysUserMapper userMapper;
    private final LostFoundPostMapper postMapper;
    private final ClaimApplicationMapper applicationMapper;
    private final SysOperationLogMapper operationLogMapper;
    private final PostService postService;
    private final ApplicationService applicationService;
    private final TokenRevocationService tokenRevocationService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<AdminUserVO> pageUsers(Long operatorId, AdminUserQuery query) {
        requireAdmin(operatorId);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(query.getRole() != null, SysUser::getRole, query.getRole())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(nested -> nested
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getNickname, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreatedAt)
                .orderByDesc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toUserVO).toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO updateUserStatus(
            Long operatorId, Long userId, UpdateUserStatusRequest request) {
        requireAdmin(operatorId);
        Map<String, Object> detail = buildDetail(
                "targetUserId", userId,
                "requestedStatus", request.status(),
                "reason", trimToNull(request.reason()));
        try {
            SysUser target = requireUser(userId);
            detail.put("beforeStatus", target.getStatus());
            if (target.getStatus() == request.status()) {
                detail.put("changed", false);
                operationLogService.recordSuccess(
                        operatorId, ACTION_USER_STATUS, TARGET_USER, userId, detail);
                return toUserVO(target);
            }

            if (Objects.equals(operatorId, userId)
                    && request.status() == UserStatus.DISABLED) {
                throw ApiException.conflict("不能禁用当前登录的管理员账号");
            }
            if (target.getRole() == UserRole.ADMIN
                    && target.getStatus() == UserStatus.ACTIVE
                    && request.status() == UserStatus.DISABLED) {
                long activeAdmins = userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getRole, UserRole.ADMIN)
                                .eq(SysUser::getStatus, UserStatus.ACTIVE));
                if (activeAdmins <= 1) {
                    throw ApiException.conflict("系统至少需要保留一个可用管理员");
                }
            }

            int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, userId)
                    .eq(SysUser::getStatus, target.getStatus())
                    .set(SysUser::getStatus, request.status())
                    .set(SysUser::getUpdatedAt, LocalDateTime.now()));
            if (updated != 1) {
                throw ApiException.conflict("用户状态已变化，请刷新后重试");
            }
            if (request.status() == UserStatus.DISABLED) {
                // 禁用后立即吊销该用户此前签发的全部 JWT。
                tokenRevocationService.invalidateAllForUser(userId);
            }
            detail.put("afterStatus", request.status());
            detail.put("changed", true);
            operationLogService.recordSuccess(
                    operatorId, ACTION_USER_STATUS, TARGET_USER, userId, detail);
            return toUserVO(requireUser(userId));
        } catch (ApiException exception) {
            detail.put("error", exception.getMessage());
            operationLogService.recordFailure(
                    operatorId, ACTION_USER_STATUS, TARGET_USER, userId, detail);
            throw exception;
        }
    }

    @Override
    public PageResult<PostVO> pagePosts(Long operatorId, AdminPostQuery query) {
        requireAdmin(operatorId);
        PostQuery postQuery = new PostQuery();
        postQuery.setPage(query.getPage());
        postQuery.setSize(query.getSize());
        postQuery.setPostType(query.getPostType());
        postQuery.setStatus(query.getStatus());
        return postService.pageForAdmin(
                operatorId, postQuery, query.getKeyword(), query.getPublisherId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO moderatePost(
            Long operatorId, Long postId, ModeratePostRequest request) {
        requireAdmin(operatorId);
        Map<String, Object> detail = buildDetail(
                "targetPostId", postId,
                "action", request.action(),
                "reason", trimToNull(request.reason()));
        String auditAction = request.action() == PostModerationAction.HIDE
                ? ACTION_POST_HIDE : ACTION_POST_RESTORE;
        try {
            LostFoundPost post = postMapper.selectById(postId);
            if (post == null) {
                throw ApiException.notFound("帖子不存在");
            }
            detail.put("beforeStatus", post.getStatus());

            LocalDateTime now = LocalDateTime.now();
            if (request.action() == PostModerationAction.HIDE) {
                if (!HIDEABLE_STATUSES.contains(post.getStatus())) {
                    throw ApiException.conflict("当前状态不能隐藏");
                }
                int updated = postMapper.update(null,
                        new LambdaUpdateWrapper<LostFoundPost>()
                                .eq(LostFoundPost::getId, postId)
                                .eq(LostFoundPost::getStatus, post.getStatus())
                                .set(LostFoundPost::getHiddenPreviousStatus, post.getStatus())
                                .set(LostFoundPost::getStatus, PostStatus.HIDDEN)
                                .set(LostFoundPost::getHiddenAt, now)
                                .set(LostFoundPost::getUpdatedAt, now));
                if (updated != 1) {
                    throw ApiException.conflict("帖子状态已变化，请刷新后重试");
                }
                detail.put("afterStatus", PostStatus.HIDDEN);
            } else {
                if (post.getStatus() != PostStatus.HIDDEN
                        || post.getHiddenPreviousStatus() == null) {
                    throw ApiException.conflict("只有被隐藏的帖子可以恢复");
                }
                PostStatus restoreStatus = post.getHiddenPreviousStatus();
                int updated = postMapper.update(null,
                        new LambdaUpdateWrapper<LostFoundPost>()
                                .eq(LostFoundPost::getId, postId)
                                .eq(LostFoundPost::getStatus, PostStatus.HIDDEN)
                                .set(LostFoundPost::getStatus, restoreStatus)
                                .set(LostFoundPost::getHiddenPreviousStatus, null)
                                .set(LostFoundPost::getHiddenAt, null)
                                .set(LostFoundPost::getUpdatedAt, now));
                if (updated != 1) {
                    throw ApiException.conflict("帖子状态已变化，请刷新后重试");
                }
                detail.put("afterStatus", restoreStatus);
            }

            operationLogService.recordSuccess(
                    operatorId, auditAction, TARGET_POST, postId, detail);
            return postService.getDetail(postId);
        } catch (ApiException exception) {
            detail.put("error", exception.getMessage());
            operationLogService.recordFailure(
                    operatorId, auditAction, TARGET_POST, postId, detail);
            throw exception;
        }
    }

    @Override
    public PageResult<ApplicationVO> pageApplications(
            Long operatorId, ApplicationQuery query) {
        requireAdmin(operatorId);
        return applicationService.pageForAdmin(operatorId, query);
    }

    @Override
    public AdminStatisticsVO statistics(Long operatorId) {
        requireAdmin(operatorId);
        return new AdminStatisticsVO(
                userMapper.selectCount(new LambdaQueryWrapper<>()),
                userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, UserStatus.ACTIVE)),
                postMapper.selectCount(new LambdaQueryWrapper<>()),
                postMapper.selectCount(new LambdaQueryWrapper<LostFoundPost>()
                        .eq(LostFoundPost::getStatus, PostStatus.PUBLISHED)),
                postMapper.selectCount(new LambdaQueryWrapper<LostFoundPost>()
                        .eq(LostFoundPost::getStatus, PostStatus.PROCESSING)),
                postMapper.selectCount(new LambdaQueryWrapper<LostFoundPost>()
                        .eq(LostFoundPost::getStatus, PostStatus.RESOLVED)),
                postMapper.selectCount(new LambdaQueryWrapper<LostFoundPost>()
                        .eq(LostFoundPost::getStatus, PostStatus.HIDDEN)),
                applicationMapper.selectCount(new LambdaQueryWrapper<>()),
                applicationMapper.selectCount(new LambdaQueryWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getStatus, ApplicationStatus.PENDING))
        );
    }

    @Override
    public PageResult<OperationLogVO> pageOperationLogs(
            Long operatorId, OperationLogQuery query) {
        requireAdmin(operatorId);
        if (query.getCreatedFrom() != null
                && query.getCreatedTo() != null
                && query.getCreatedFrom().isAfter(query.getCreatedTo())) {
            throw ApiException.badRequest("日志开始时间不能晚于结束时间");
        }
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(query.getOperatorId() != null,
                        SysOperationLog::getOperatorId, query.getOperatorId())
                .eq(StringUtils.hasText(query.getAction()),
                        SysOperationLog::getAction, trimToNull(query.getAction()))
                .eq(StringUtils.hasText(query.getTargetType()),
                        SysOperationLog::getTargetType, trimToNull(query.getTargetType()))
                .eq(query.getTargetId() != null,
                        SysOperationLog::getTargetId, query.getTargetId())
                .eq(query.getResult() != null,
                        SysOperationLog::getResult, query.getResult())
                .ge(query.getCreatedFrom() != null,
                        SysOperationLog::getCreatedAt, query.getCreatedFrom())
                .le(query.getCreatedTo() != null,
                        SysOperationLog::getCreatedAt, query.getCreatedTo())
                .orderByDesc(SysOperationLog::getCreatedAt)
                .orderByDesc(SysOperationLog::getId);
        Page<SysOperationLog> page = operationLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        Set<Long> userIds = page.getRecords().stream()
                .map(SysOperationLog::getOperatorId)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        Map<Long, SysUser> users = new HashMap<>();
        for (SysUser user : userMapper.selectByIds(userIds)) {
            users.put(user.getId(), user);
        }
        List<OperationLogVO> records = new ArrayList<>(page.getRecords().size());
        for (SysOperationLog log : page.getRecords()) {
            SysUser operator = users.get(log.getOperatorId());
            records.add(new OperationLogVO(
                    log.getId(),
                    operator == null ? null
                            : new UserSummaryVO(
                                    operator.getId(),
                                    operator.getNickname(),
                                    operator.getAvatarUrl()),
                    log.getAction(),
                    log.getTargetType(),
                    log.getTargetId(),
                    log.getResult(),
                    parseDetail(log.getDetailJson()),
                    log.getCreatedAt()
            ));
        }
        return new PageResult<>(
                records, page.getCurrent(), page.getSize(), page.getTotal());
    }

    private void requireAdmin(Long operatorId) {
        SysUser operator = userMapper.selectById(operatorId);
        if (operator == null
                || operator.getStatus() != UserStatus.ACTIVE
                || operator.getRole() != UserRole.ADMIN) {
            throw ApiException.forbidden("仅启用状态的管理员可以执行该操作");
        }
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ApiException.notFound("用户不存在");
        }
        return user;
    }

    private AdminUserVO toUserVO(SysUser user) {
        return new AdminUserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private JsonNode parseDetail(String detailJson) {
        if (!StringUtils.hasText(detailJson)) {
            return null;
        }
        try {
            return objectMapper.readTree(detailJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的管理操作详情格式不正确", exception);
        }
    }

    private Map<String, Object> buildDetail(Object... pairs) {
        Map<String, Object> detail = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            Object value = pairs[index + 1];
            if (value != null) {
                detail.put(String.valueOf(pairs[index]), value);
            }
        }
        return detail;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
