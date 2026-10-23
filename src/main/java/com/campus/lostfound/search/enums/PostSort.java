package com.campus.lostfound.search.enums;

/**
 * 公开帖子搜索支持的排序方式。
 *
 * <p>使用受控枚举而不是直接接收数据库列名或 ORDER BY 片段，避免客户端改变 SQL 结构。</p>
 */
public enum PostSort {

    /**
     * 按发布时间倒序，适合首页和默认搜索结果。
     */
    LATEST,

    /**
     * 按事件发生时间倒序，适合优先查看最近发生的信息。
     */
    OCCURRENCE_DESC,

    /**
     * 按事件发生时间正序。
     */
    OCCURRENCE_ASC
}
