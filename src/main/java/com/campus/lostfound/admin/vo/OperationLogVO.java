package com.campus.lostfound.admin.vo;

import com.campus.lostfound.admin.enums.OperationResult;
import com.campus.lostfound.post.vo.UserSummaryVO;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 审计日志视图，detail 从数据库 JSON 文本解析而来。
 */
public record OperationLogVO(
        Long id,
        UserSummaryVO operator,
        String action,
        String targetType,
        Long targetId,
        OperationResult result,
        JsonNode detail,
        LocalDateTime createdAt
) {
}
