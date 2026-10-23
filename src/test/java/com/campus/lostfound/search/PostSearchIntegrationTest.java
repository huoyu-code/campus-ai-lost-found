package com.campus.lostfound.search;

import com.campus.lostfound.post.entity.LostFoundPost;
import com.campus.lostfound.post.enums.ContactVisibility;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;
import com.campus.lostfound.post.mapper.LostFoundPostMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MySQL 兼容环境下的公开帖子搜索集成测试。
 *
 * <p>直接插入不同可见状态和排序时间的帖子，验证关键词匹配、组合筛选、
 * 可见性隔离、稳定排序、分页上限以及 SQL 参数绑定行为。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LostFoundPostMapper postMapper;

    @Test
    void searchesCombinedFiltersFiltersVisibilityAndSorts() throws Exception {
        // 每次使用随机关键词，避免同一测试上下文中的其他帖子干扰断言。
        String keyword = "search" + UUID.randomUUID().toString().replace("-", "");
        long firstId = insertPost(
                keyword + " 校园卡",
                null,
                "证件卡片",
                "图书馆一楼",
                PostType.FOUND,
                PostStatus.PUBLISHED,
                LocalDateTime.of(2026, 10, 20, 10, 0),
                LocalDateTime.of(2026, 10, 20, 10, 0)
        );
        long secondId = insertPost(
                "复习资料",
                keyword + " 高数笔记",
                "书籍资料",
                "第二教学楼",
                PostType.LOST,
                PostStatus.PUBLISHED,
                LocalDateTime.of(2026, 10, 22, 10, 0),
                LocalDateTime.of(2026, 10, 20, 11, 0)
        );
        long thirdId = insertPost(
                "实验室耳机",
                keyword + " 白色充电盒",
                "电子数码",
                "实验楼",
                PostType.FOUND,
                PostStatus.PROCESSING,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                LocalDateTime.of(2026, 10, 20, 12, 0)
        );

        // 隐藏和关闭帖子也包含关键词，但不应出现在公开搜索结果中。
        insertPost(
                keyword + " 已隐藏",
                null,
                "其他",
                "体育馆",
                PostType.FOUND,
                PostStatus.HIDDEN,
                LocalDateTime.of(2026, 10, 23, 10, 0),
                LocalDateTime.of(2026, 10, 20, 13, 0)
        );
        insertPost(
                keyword + " 已关闭",
                null,
                "其他",
                "食堂",
                PostType.LOST,
                PostStatus.CLOSED,
                LocalDateTime.of(2026, 10, 24, 10, 0),
                LocalDateTime.of(2026, 10, 20, 14, 0)
        );

        mockMvc.perform(get("/search/posts")
                        .queryParam("keyword", keyword)
                        .queryParam("postType", "FOUND")
                        .queryParam("category", "证件卡片")
                        .queryParam("status", "PUBLISHED")
                        .queryParam("location", "图书馆")
                        .queryParam("occurrenceFrom", "2026-10-20T00:00:00")
                        .queryParam("occurrenceTo", "2026-10-21T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(firstId))
                .andExpect(jsonPath("$.data.records[0].description").isEmpty());

        // 默认按发布时间倒序；公开状态为 PUBLISHED、PROCESSING 和 RESOLVED。
        mockMvc.perform(get("/search/posts")
                        .queryParam("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(thirdId))
                .andExpect(jsonPath("$.data.records[1].id").value(secondId))
                .andExpect(jsonPath("$.data.records[2].id").value(firstId));

        // 按事件发生时间倒序，并使用第二页验证 OFFSET 和总数。
        mockMvc.perform(get("/search/posts")
                        .queryParam("keyword", keyword)
                        .queryParam("sort", "OCCURRENCE_DESC")
                        .queryParam("page", "2")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(firstId));

        // 公开搜索不允许借 status 参数读取隐藏数据。
        mockMvc.perform(get("/search/posts")
                        .queryParam("keyword", keyword)
                        .queryParam("status", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void rejectsUnboundedPagingAndTreatsInjectionTextAsValue() throws Exception {
        // 引号、OR 和注释符只能作为普通搜索文本，不会改变 WHERE 条件。
        mockMvc.perform(get("/search/posts")
                        .queryParam("keyword", "' OR 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/search/posts")
                        .queryParam("page", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("页码不能超过 1000"));

        mockMvc.perform(get("/search/posts")
                        .queryParam("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("每页条数不能超过 50"));
    }

    private long insertPost(
            String title,
            String description,
            String category,
            String location,
            PostType postType,
            PostStatus status,
            LocalDateTime occurrenceTime,
            LocalDateTime createdAt
    ) {
        LostFoundPost post = LostFoundPost.builder()
                .publisherId(999_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000))
                .postType(postType)
                .title(title)
                .description(description)
                .category(category)
                .location(location)
                .occurrenceTime(occurrenceTime)
                .contactText(null)
                .contactVisibility(ContactVisibility.OWNER_ONLY)
                .status(status)
                .hiddenPreviousStatus(
                        status == PostStatus.HIDDEN ? PostStatus.PUBLISHED : null)
                .hiddenAt(status == PostStatus.HIDDEN ? createdAt : null)
                .closedAt(status == PostStatus.CLOSED ? createdAt : null)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        postMapper.insert(post);
        return post.getId();
    }
}
