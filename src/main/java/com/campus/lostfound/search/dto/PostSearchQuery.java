package com.campus.lostfound.search.dto;

import com.campus.lostfound.post.dto.PostQuery;
import com.campus.lostfound.search.enums.PostSort;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * MySQL 帖子搜索条件。
 *
 * <p>复用帖子模块已有的类型、分类、状态、地点和时间筛选，仅追加关键词和排序。
 * 所有文本最终都通过 MyBatis 参数绑定，不会把用户输入拼接到 SQL 中。</p>
 */
@Getter
@Setter
public class PostSearchQuery extends PostQuery {

    @Size(max = 100, message = "搜索关键词不能超过 100 个字符")
    private String keyword;

    private PostSort sort = PostSort.LATEST;
}
