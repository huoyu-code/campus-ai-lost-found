package com.campus.lostfound.admin.vo;

/**
 * 管理后台基础业务统计。
 */
public record AdminStatisticsVO(
        long totalUsers,
        long activeUsers,
        long totalPosts,
        long publishedPosts,
        long processingPosts,
        long resolvedPosts,
        long hiddenPosts,
        long totalApplications,
        long pendingApplications
) {
}
