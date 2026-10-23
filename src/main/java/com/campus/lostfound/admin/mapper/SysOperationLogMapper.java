package com.campus.lostfound.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.admin.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理操作日志数据访问入口。
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
