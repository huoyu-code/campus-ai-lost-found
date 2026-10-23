package com.campus.lostfound.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.campus.lostfound.user.entity.SysUser;
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

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private static final Set<String> CATEGORIES = Set.of(
            "证件卡片", "电子数码", "书籍资料", "生活用品", "服饰鞋包", "钥匙饰品", "其他");

    private final LostFoundPostMapper postMapper;
    private final PostImageMapper imageMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO create(Long userId, CreatePostRequest request) {
        String category = validateCategory(request.category());
        LocalDateTime now = LocalDateTime.now();
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
    public PageResult<PostVO> pageMine(Long userId, PostQuery query) {
        validateRange(query);
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
    @Transactional(rollbackFor = Exception.class)
    public PostVO update(Long postId, Long userId, UpdatePostRequest request) {
        LostFoundPost existing = requirePost(postId);
        requireOwner(existing, userId);
        if (existing.getStatus() != PostStatus.PUBLISHED) {
            throw ApiException.conflict("只有已发布状态的帖子可以编辑");
        }

        String category = validateCategory(request.category());
        LocalDateTime now = LocalDateTime.now();
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
        if (request.targetStatus() != PostStatus.CLOSED) {
            throw ApiException.badRequest("该接口当前只支持关闭帖子");
        }
        if (post.getStatus() == PostStatus.CLOSED) {
            return getDetail(postId);
        }
        if (post.getStatus() == PostStatus.HIDDEN
                || post.getStatus() == PostStatus.RESOLVED) {
            throw ApiException.conflict("当前状态不能关闭帖子");
        }

        LocalDateTime now = LocalDateTime.now();
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
        return getDetail(postId);
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
        for (SysUser user : userMapper.selectBatchIds(userIds)) {
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
        if (owner || admin) {
            return true;
        }
        LoginUser viewer = UserContext.get();
        if (viewer == null) {
            return false;
        }
        return post.getContactVisibility() == ContactVisibility.AUTHENTICATED;
        // ACCEPTED_ONLY will additionally allow the accepted applicant in stage 5.
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

