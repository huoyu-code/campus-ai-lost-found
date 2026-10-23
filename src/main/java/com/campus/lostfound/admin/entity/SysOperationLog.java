package com.campus.lostfound.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.lostfound.admin.enums.OperationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员操作审计日志，对应 {@code sys_operation_log}。
 *
 * <p>{@code detailJson} 保存与具体动作相关的结构化上下文，例如状态变化前后值和原因。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String action;

    private String targetType;

    private Long targetId;

    private OperationResult result;

    private String detailJson;

    private LocalDateTime createdAt;
}
