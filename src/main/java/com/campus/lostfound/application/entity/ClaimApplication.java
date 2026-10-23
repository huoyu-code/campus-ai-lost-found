package com.campus.lostfound.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.lostfound.application.enums.ApplicationStatus;
import com.campus.lostfound.application.enums.ApplicationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 认领/线索申请实体，对应 {@code claim_application}。
 *
 * <p>数据库通过生成列和唯一索引保证“单用户单帖最多一个有效申请”以及
 * “单帖最多一个已接受申请”；实体中不映射这两个只读生成列。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("claim_application")
public class ClaimApplication {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long applicantId;

    private ApplicationType applicationType;

    private String description;

    private ApplicationStatus status;

    private Long processedBy;

    private String processNote;

    private LocalDateTime processedAt;

    private LocalDateTime completionRequestedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
