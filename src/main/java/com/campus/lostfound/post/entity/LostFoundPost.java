package com.campus.lostfound.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.lostfound.post.enums.ContactVisibility;
import com.campus.lostfound.post.enums.PostStatus;
import com.campus.lostfound.post.enums.PostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 失物/招领统一帖子实体，对应 {@code lost_found_post}。
 *
 * <p>{@code publisherId} 是应用层维护的逻辑外键，数据库不创建物理外键。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("lost_found_post")
public class LostFoundPost {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long publisherId;

    private PostType postType;

    private String title;

    private String description;

    private String category;

    private String location;

    private LocalDateTime occurrenceTime;

    private String contactText;

    private ContactVisibility contactVisibility;

    private PostStatus status;

    private PostStatus hiddenPreviousStatus;

    private LocalDateTime resolvedAt;

    private LocalDateTime closedAt;

    private LocalDateTime hiddenAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

