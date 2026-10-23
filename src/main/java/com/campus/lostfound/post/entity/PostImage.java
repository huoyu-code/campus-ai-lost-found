package com.campus.lostfound.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 帖子图片 URL 实体，同一帖子的图片用小整数排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("lost_found_post_image")
public class PostImage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long postId;

    private String imageUrl;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}

