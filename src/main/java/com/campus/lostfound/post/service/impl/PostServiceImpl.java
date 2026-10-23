package com.campus.lostfound.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.application.entity.ClaimApplication;
import com.campus.lostfound.application.enums.ApplicationStatus;
import com.campus.lostfound.application.mapper.ClaimApplicationMapper;
import com.campus.lostfound.common.api.PageResult;
import com.campus.lostfound.common.exception.ApiException;
import com.campus.lostfound.common.security.LoginUser;
import com.campus.lostfound.common.security.UserContext;
import com.campus.lostfound.post.dto.CreatePostRequest;
import com.campus.lostfound.post.dto.PostQuery;
import com.campus.lostfound.post.dto.UpdatePostRequest;
import com.campus.lostfound.post.dto.UpdatePostStatusRequest;
import com.campus.lostfound.post.entity.LostFoundPost;
import com.campus.lostfound.post.entity.PostImage;
import com.campus.lostfound.post.enums.ContactVisibility;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;
import com.campus.lostfound.post.mapper.LostFoundPostMapper;
import com.campus.lostfound.post.mapper.PostImageMapper;
import com.campus.lostfound.post.service.PostService;
import com.campus.lostfound.post.vo.PostVO;
import com.campus.lostfound.post.vo.UserSummaryVO;
import com.campus.lostfound.search.dto.PostSearchQuery;
import com.campus.lostfound.search.enums.PostSort;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 帖子业务实现。
 *
 * <p>核心原则是：前端传入的数据只作为候选值，状态、所有权和联系方式可见性都必须
 * 在服务端重新判断。涉及状态迁移的写操作使用“当前状态作为 WHERE 条件”的条件更新，
 * 以识别并发冲突而不是静默覆盖。</p>
 */
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    // 分类暂时作为受控常量维护，保证数据库 CHECK 约束与应用校验一致。
    private static final Set<String> CATEGORIES = Set.of(
            "证件卡片", "电子数码", "书籍资料", "生活用品", "服饰鞋包", "钥匙饰品", "其他");

    private final LostFoundPostMapper postMapper;
    private final PostImageMapper imageMapper;
    private final ClaimApplicationMapper applicationMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO create(Long userId, CreatePostRequest request) {
        String category = validateCategory(request.category());
        LocalDateTime now = LocalDateTime.now();
        // MVP 不做发布审核，新帖子直接进入公开状态。
        LostFoundPost post = LostFoundPost.builder()
                .publisherId(userId)
                .postType(request.postType())
                .title(request.title().trim())
                .description(trimToNull(request.description()))
                .category(category)
                .location(request.location().trim())
                .occurrenceTime(request.occurrenceTime())
                .contactText(trimToNull(request.contactText()))
                .contactVisibility(defaultContactVisibility(request.contactVisibility()))
                .status(PostStatus.PUBLISHED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        postMapper.insert(post);
        replaceImages(post.getId(), request.imageUrls(), now);
        return getDetail(post.getId());
    }

    @Override
    public PostVO getDetail(Long postId) {
        LostFoundPost post = requirePost(postId);
        // 关闭和隐藏帖子只对发布者或管理员可见，使用 404 避免泄露资源是否存在。
        if (!canView(post)) {
            throw ApiException.notFound("帖子不存在或不可见");
        }
        return assemble(List.of(post), true).getFirst();
    }

    @Override
    public PageResult<PostVO> pagePublic(PostQuery query) {
        validateRange(query);
        LambdaQueryWrapper<LostFoundPost> wrapper = baseQuery(query);
        if (query.getStatus() != null) {
            // 请求非公开状态时返回空页，不把 CLOSED/HIDDEN 数据暴露给游客。
            if (!query.getStatus().isPubliclyVisible()) {
                return new PageResult<>(List.of(), query.getPage(), query.getSize(), 0);
            }
            wrapper.eq(LostFoundPost::getStatus, query.getStatus());
        } else {
            wrapper.in(LostFoundPost::getStatus, PostStatus.publiclyVisible());
        }
        wrapper.orderByDesc(LostFoundPost::getCreatedAt)
                .orderByDesc(LostFoundPost::getId);
        return page(wrapper, query);
    }

    @Override
    public PageResult<PostVO> searchPublic(PostSearchQuery query) {
        validateRange(query);
        LambdaQueryWrapper<LostFoundPost> wrapper = baseQuery(query);

        // 关键词只匹配普通用户可搜索的文本字段。Wrapper 会生成参数占位符，
        // 即使用户输入 SQL 片段也只会被当作普通字符串值。
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested
                    .like(LostFoundPost::getTitle, keyword)
                    .or()
                    .like(LostFoundPost::getDescription, keyword)
                    .or()
                    .like(LostFoundPost::getLocation, keyword));
        }

        if (query.getStatus() != null) {
            // 公开搜索不提供管理员的全状态查询能力；请求隐藏或关闭状态时返回空页，
            // 同时不泄露这些状态是否存在数据。
            if (!query.getStatus().isPubliclyVisible()) {
                return new PageResult<>(List.of(), query.getPage(), query.getSize(), 0);
            }
            wrapper.eq(LostFoundPost::getStatus, query.getStatus());
        } else {
            wrapper.in(LostFoundPost::getStatus, PostStatus.publiclyVisible());
        }

        applySearchSort(wrapper, query.getSort());
        return page(wrapper, query);
    }

    @Override
    public PageResult<PostVO> pageMine(Long userId, PostQuery query) {
        validateRange(query);
        // “我的帖子”不加公开状态过滤，让发布者能看到 CLOSED/HIDDEN 历史。
        LambdaQueryWrapper<LostFoundPost> wrapper = baseQuery(query)
                .eq(LostFoundPost::getPublisherId, userId);
        if (query.getStatus() != null) {
            wrapper.eq(LostFoundPost::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(LostFoundPost::getCreatedAt)
                .orderByDesc(LostFoundPost::getId);
        return page(wrapper, query);
    }

    @Override
    public PageResult<PostVO> pageForAdmin(
            Long operatorId, PostQuery query, String keyword, Long publisherId) {
        SysUser operator = userMapper.selectById(operatorId);
        if (operator == null
                || operator.getStatus() != UserStatus.ACTIVE
                || operator.getRole() != UserRole.ADMIN) {
            throw ApiException.forbidden("仅管理员可以查看全平台帖子");
        }
        validateRange(query);
        LambdaQueryWrapper<LostFoundPost> wrapper = baseQuery(query)
                .eq(query.getStatus() != null,
                        LostFoundPost::getStatus, query.getStatus())
                .eq(publisherId != null, LostFoundPost::getPublisherId, publisherId);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(nested -> nested
                    .like(LostFoundPost::getTitle, normalizedKeyword)
                    .or()
                    .like(LostFoundPost::getDescription, normalizedKeyword)
                    .or()
                    .like(LostFoundPost::getLocation, normalizedKeyword));
        }
        wrapper.orderByDesc(LostFoundPost::getCreatedAt)
                .orderByDesc(LostFoundPost::getId);
        Page<LostFoundPost> page = postMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                assemble(page.getRecords(), true),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO update(Long postId, Long userId, UpdatePostRequest request) {
        LostFoundPost existing = requirePost(postId);
        requireOwner(existing, userId);
        if (existing.getStatus() != PostStatus.PUBLISHED) {
            throw ApiException.conflict("只有已发布状态的帖子可以编辑");
        }
        // 已有申请时修改 LOST/FOUND 会让 CLAIM/CLUE 类型失去意义。
        if (request.postType() != existing.getPostType()
                && applicationMapper.selectCount(
                        new LambdaQueryWrapper<ClaimApplication>()
                                .eq(ClaimApplication::getPostId, postId)) > 0) {
            throw ApiException.conflict("已有申请的帖子不能修改帖子类型");
        }

        String category = validateCategory(request.category());
        LocalDateTime now = LocalDateTime.now();
        // WHERE 同时带状态和发布者条件，防止并发状态变化后覆盖新数据。
        int updated = postMapper.update(null, new LambdaUpdateWrapper<LostFoundPost>()
                .eq(LostFoundPost::getId, postId)
                .eq(LostFoundPost::getPublisherId, userId)
                .eq(LostFoundPost::getStatus, PostStatus.PUBLISHED)
                .set(LostFoundPost::getPostType, request.postType())
                .set(LostFoundPost::getTitle, request.title().trim())
                .set(LostFoundPost::getDescription, trimToNull(request.description()))
                .set(LostFoundPost::getCategory, category)
                .set(LostFoundPost::getLocation, request.location().trim())
                .set(LostFoundPost::getOccurrenceTime, request.occurrenceTime())
                .set(LostFoundPost::getContactText, trimToNull(request.contactText()))
                .set(LostFoundPost::getContactVisibility,
                        defaultContactVisibility(request.contactVisibility()))
                .set(LostFoundPost::getUpdatedAt, now));
        if (updated != 1) {
            throw ApiException.conflict("帖子状态已变化，请刷新后重试");
        }
        if (request.imageUrls() != null) {
            replaceImages(postId, request.imageUrls(), now);
        }
        return getDetail(postId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO updateStatus(
            Long postId, Long userId, UpdatePostStatusRequest request) {
        LostFoundPost post = requirePost(postId);
        requireOwner(post, userId);
        if (request.targetStatus() != PostStatus.CLOSED
                && request.targetStatus() != PostStatus.RESOLVED) {
            throw ApiException.badRequest("该接口只支持关闭或确认完成帖子");
        }
        // 两个目标状态需要不同业务校验，因此分别进入独立方法。
        return request.targetStatus() == PostStatus.CLOSED
                ? closePost(post, userId)
                : resolvePost(post, userId);
    }

    private PostVO closePost(LostFoundPost post, Long userId) {
        Long postId = post.getId();
        if (post.getStatus() == PostStatus.CLOSED) {
            return getDetail(postId);
        }
        if (post.getStatus() == PostStatus.HIDDEN
                || post.getStatus() == PostStatus.RESOLVED) {
            throw ApiException.conflict("当前状态不能关闭帖子");
        }

        LocalDateTime now = LocalDateTime.now();
        // 条件更新相当于一个轻量状态锁：只有一个请求能把当前状态推进到 CLOSED。
        int updated = postMapper.update(null, new LambdaUpdateWrapper<LostFoundPost>()
                .eq(LostFoundPost::getId, postId)
                .eq(LostFoundPost::getPublisherId, userId)
                .in(LostFoundPost::getStatus, PostStatus.PUBLISHED, PostStatus.PROCESSING)
                .set(LostFoundPost::getStatus, PostStatus.CLOSED)
                .set(LostFoundPost::getClosedAt, now)
                .set(LostFoundPost::getUpdatedAt, now));
        if (updated != 1) {
            throw ApiException.conflict("帖子状态已变化，请刷新后重试");
        }
        // 关闭后待处理申请不再有业务意义，批量拒绝但保留历史记录。
        applicationMapper.update(null,
                new LambdaUpdateWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getPostId, postId)
                        .eq(ClaimApplication::getStatus, ApplicationStatus.PENDING)
                        .set(ClaimApplication::getStatus, ApplicationStatus.REJECTED)
                        .set(ClaimApplication::getProcessedBy, userId)
                        .set(ClaimApplication::getProcessNote, "帖子已由发布者关闭")
                        .set(ClaimApplication::getProcessedAt, now)
                        .set(ClaimApplication::getUpdatedAt, now));
        return getDetail(postId);
    }

    private PostVO resolvePost(LostFoundPost post, Long userId) {
        if (post.getStatus() != PostStatus.PROCESSING) {
            throw ApiException.conflict("只有处理中的帖子可以确认完成");
        }
        ClaimApplication accepted = applicationMapper.selectOne(
                new LambdaQueryWrapper<ClaimApplication>()
                        .eq(ClaimApplication::getPostId, post.getId())
                        .eq(ClaimApplication::getStatus, ApplicationStatus.ACCEPTED));
        if (accepted == null || accepted.getCompletionRequestedAt() == null) {
            throw ApiException.conflict("申请人尚未发起完成请求");
        }

        LocalDateTime now = LocalDateTime.now();
        // 必须同时满足：当前状态 PROCESSING、申请已接受、申请人已请求完成。
        int updated = postMapper.update(null, new LambdaUpdateWrapper<LostFoundPost>()
                .eq(LostFoundPost::getId, post.getId())
                .eq(LostFoundPost::getPublisherId, userId)
                .eq(LostFoundPost::getStatus, PostStatus.PROCESSING)
                .set(LostFoundPost::getStatus, PostStatus.RESOLVED)
                .set(LostFoundPost::getResolvedAt, now)
                .set(LostFoundPost::getUpdatedAt, now));
        if (updated != 1) {
            throw ApiException.conflict("帖子状态已变化，请刷新后重试");
        }
        return getDetail(post.getId());
    }

    private PageResult<PostVO> page(
            LambdaQueryWrapper<LostFoundPost> wrapper, PostQuery query) {
        Page<LostFoundPost> page = postMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                assemble(page.getRecords(), false),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    private LambdaQueryWrapper<LostFoundPost> baseQuery(PostQuery query) {
        LambdaQueryWrapper<LostFoundPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getPostType() != null,
                        LostFoundPost::getPostType, query.getPostType())
                .eq(StringUtils.hasText(query.getCategory()),
                        LostFoundPost::getCategory, trimToNull(query.getCategory()))
                .like(StringUtils.hasText(query.getLocation()),
                        LostFoundPost::getLocation, trimToNull(query.getLocation()))
                .ge(query.getOccurrenceFrom() != null,
                        LostFoundPost::getOccurrenceTime, query.getOccurrenceFrom())
                .le(query.getOccurrenceTo() != null,
                        LostFoundPost::getOccurrenceTime, query.getOccurrenceTo());
        if (StringUtils.hasText(query.getCategory())) {
            validateCategory(query.getCategory());
        }
        return wrapper;
    }

    private void applySearchSort(
            LambdaQueryWrapper<LostFoundPost> wrapper, PostSort sort) {
        // 每种排序都追加 id 作为第二排序键，确保同一时间的数据有稳定顺序，
        // 否则相邻分页可能重复或遗漏记录。
        PostSort effectiveSort = sort == null ? PostSort.LATEST : sort;
        switch (effectiveSort) {
            case LATEST -> wrapper
                    .orderByDesc(LostFoundPost::getCreatedAt)
                    .orderByDesc(LostFoundPost::getId);
            case OCCURRENCE_DESC -> wrapper
                    .orderByDesc(LostFoundPost::getOccurrenceTime)
                    .orderByDesc(LostFoundPost::getId);
            case OCCURRENCE_ASC -> wrapper
                    .orderByAsc(LostFoundPost::getOccurrenceTime)
                    .orderByAsc(LostFoundPost::getId);
        }
    }

    private void validateRange(PostQuery query) {
        if (query.getOccurrenceFrom() != null
                && query.getOccurrenceTo() != null
                && query.getOccurrenceFrom().isAfter(query.getOccurrenceTo())) {
            throw ApiException.badRequest("开始时间不能晚于结束时间");
        }
    }

    private List<PostVO> assemble(List<LostFoundPost> posts, boolean detail) {
        if (posts.isEmpty()) {
            return List.of();
        }
        // 批量查询图片和用户，避免列表渲染时产生 N+1 查询。
        List<Long> postIds = posts.stream().map(LostFoundPost::getId).toList();
        List<PostImage> imageRows = imageMapper.selectList(new LambdaQueryWrapper<PostImage>()
                .in(PostImage::getPostId, postIds)
                .orderByAsc(PostImage::getPostId)
                .orderByAsc(PostImage::getSortOrder));
        Map<Long, List<String>> imagesByPost = new HashMap<>();
        for (PostImage row : imageRows) {
            imagesByPost.computeIfAbsent(row.getPostId(), ignored -> new ArrayList<>())
                    .add(row.getImageUrl());
        }

        Set<Long> userIds = posts.stream()
                .map(LostFoundPost::getPublisherId)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        Map<Long, SysUser> users = new HashMap<>();
        for (SysUser user : userMapper.selectByIds(userIds)) {
            users.put(user.getId(), user);
        }

        LoginUser viewer = UserContext.get();
        List<PostVO> result = new ArrayList<>(posts.size());
        for (LostFoundPost post : posts) {
            List<String> images = List.copyOf(imagesByPost.getOrDefault(
                    post.getId(), Collections.emptyList()));
            SysUser publisher = users.get(post.getPublisherId());
            boolean owner = viewer != null && Objects.equals(viewer.userId(), post.getPublisherId());
            boolean admin = viewer != null && viewer.isAdmin();
            // detail=false 时永远不返回联系方式，减少列表接口的敏感数据暴露面。
            boolean contactVisible = detail && canViewContact(post, owner, admin);

            result.add(new PostVO(
                    post.getId(),
                    post.getPostType(),
                    post.getTitle(),
                    detail ? post.getDescription() : null,
                    post.getCategory(),
                    post.getLocation(),
                    post.getOccurrenceTime(),
                    contactVisible ? post.getContactText() : null,
                    post.getContactVisibility(),
                    post.getStatus(),
                    images,
                    images.isEmpty() ? null : images.getFirst(),
                    publisher == null ? null
                            : new UserSummaryVO(
                                    publisher.getId(),
                                    publisher.getNickname(),
                                    publisher.getAvatarUrl()),
                    owner,
                    post.getResolvedAt(),
                    post.getClosedAt(),
                    post.getCreatedAt(),
                    post.getUpdatedAt()
            ));
        }
        return result;
    }

    private boolean canView(LostFoundPost post) {
        LoginUser viewer = UserContext.get();
        // 公开状态不要求登录；非公开状态仅发布者和管理员可访问。
        if (post.getStatus().isPubliclyVisible()) {
            return true;
        }
        return viewer != null
                && (viewer.isAdmin() || Objects.equals(viewer.userId(), post.getPublisherId()));
    }

    private boolean canViewContact(LostFoundPost post, boolean owner, boolean admin) {
        if (!StringUtils.hasText(post.getContactText())) {
            return false;
        }
        // 发布者和管理员始终可见自己的联系方式。
        if (owner || admin) {
            return true;
        }
        LoginUser viewer = UserContext.get();
        if (viewer == null) {
            return false;
        }
        if (post.getContactVisibility() == ContactVisibility.AUTHENTICATED) {
            return true;
        }
        // ACCEPTED_ONLY 通过查询已接受申请判断，不信任前端传入的角色或申请状态。
        return post.getContactVisibility() == ContactVisibility.ACCEPTED_ONLY
                && applicationMapper.selectCount(
                        new LambdaQueryWrapper<ClaimApplication>()
                                .eq(ClaimApplication::getPostId, post.getId())
                                .eq(ClaimApplication::getApplicantId, viewer.userId())
                                .eq(ClaimApplication::getStatus,
                                        ApplicationStatus.ACCEPTED)) > 0;
    }

    private LostFoundPost requirePost(Long postId) {
        LostFoundPost post = postMapper.selectById(postId);
        if (post == null) {
            throw ApiException.notFound("帖子不存在");
        }
        return post;
    }

    private void requireOwner(LostFoundPost post, Long userId) {
        if (!Objects.equals(post.getPublisherId(), userId)) {
            throw ApiException.forbidden("只有发布者可以执行该操作");
        }
    }

    private void replaceImages(Long postId, List<String> imageUrls, LocalDateTime now) {
        // 图片集合采用“先删后建”的全量替换语义，PUT 请求可以清空图片。
        imageMapper.delete(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, postId));
        if (imageUrls == null) {
            return;
        }
        int sort = 0;
        for (String imageUrl : imageUrls) {
            if (!StringUtils.hasText(imageUrl)) {
                continue;
            }
            imageMapper.insert(PostImage.builder()
                    .postId(postId)
                    .imageUrl(imageUrl.trim())
                    .sortOrder(sort++)
                    .createdAt(now)
                    .build());
        }
    }

    private String validateCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        if (!CATEGORIES.contains(normalized)) {
            throw ApiException.badRequest("物品分类不合法");
        }
        return normalized;
    }

    private ContactVisibility defaultContactVisibility(ContactVisibility visibility) {
        return visibility == null ? ContactVisibility.ACCEPTED_ONLY : visibility;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

