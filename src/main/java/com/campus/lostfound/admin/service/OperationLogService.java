package com.campus.lostfound.admin.service;

import com.campus.lostfound.admin.entity.SysOperationLog;
import com.campus.lostfound.admin.enums.OperationResult;
import com.campus.lostfound.admin.mapper.SysOperationLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理操作审计服务。
 *
 * <p>成功日志加入管理操作本身的数据库事务，保证状态修改与审计记录同时提交。
 * 失败日志使用独立事务，避免因主事务回滚而丢失失败原因。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(
            Long operatorId,
            String action,
            String targetType,
            Long targetId,
            Map<String, Object> detail) {
        insert(operatorId, action, targetType, targetId, OperationResult.SUCCESS, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            Long operatorId,
            String action,
            String targetType,
            Long targetId,
            Map<String, Object> detail) {
        try {
            insert(operatorId, action, targetType, targetId, OperationResult.FAILED, detail);
        } catch (RuntimeException exception) {
            // 审计失败不能覆盖调用方真正需要看到的业务异常。
            log.warn("写入管理操作失败日志时出现异常, action={}, targetType={}, targetId={}",
                    action, targetType, targetId, exception);
        }
    }

    private void insert(
            Long operatorId,
            String action,
            String targetType,
            Long targetId,
            OperationResult result,
            Map<String, Object> detail) {
        logMapper.insert(SysOperationLog.builder()
                .operatorId(operatorId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .result(result)
                .detailJson(toJson(detail))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("管理操作详情无法序列化", exception);
        }
    }
}
