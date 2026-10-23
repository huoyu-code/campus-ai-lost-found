package com.campus.lostfound.admin.dto;

import com.campus.lostfound.admin.enums.OperationResult;
import com.campus.lostfound.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 管理操作日志筛选条件。
 */
@Getter
@Setter
public class OperationLogQuery extends PageQuery {

    private Long operatorId;

    private String action;

    private String targetType;

    private Long targetId;

    private OperationResult result;

    private LocalDateTime createdFrom;

    private LocalDateTime createdTo;
}
