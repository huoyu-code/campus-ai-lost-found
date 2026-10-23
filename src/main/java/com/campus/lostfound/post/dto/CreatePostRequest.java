package com.campus.lostfound.post.dto;

import com.campus.lostfound.post.enums.ContactVisibility;
import com.campus.lostfound.post.enums.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreatePostRequest(
        @NotNull(message = "帖子类型不能为空")
        PostType postType,

        @NotBlank(message = "标题不能为空")
        @Size(min = 2, max = 100, message = "标题长度需为 2-100 个字符")
        String title,

        @Size(max = 5000, message = "描述不能超过 5000 个字符")
        String description,

        @NotBlank(message = "分类不能为空")
        @Size(max = 30, message = "分类不能超过 30 个字符")
        String category,

        @NotBlank(message = "地点不能为空")
        @Size(max = 120, message = "地点不能超过 120 个字符")
        String location,

        @NotNull(message = "发生时间不能为空")
        LocalDateTime occurrenceTime,

        @Size(max = 100, message = "联系方式不能超过 100 个字符")
        String contactText,

        ContactVisibility contactVisibility,

        @Size(max = 6, message = "图片不能超过 6 张")
        List<
                @NotBlank(message = "图片 URL 不能为空")
                @Size(max = 500, message = "图片 URL 不能超过 500 个字符")
                @Pattern(regexp = "^https?://.+", message = "图片必须是 HTTP(S) URL")
                String
        > imageUrls
) {
}

