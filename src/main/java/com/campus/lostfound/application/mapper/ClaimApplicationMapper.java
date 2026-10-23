package com.campus.lostfound.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.application.entity.ClaimApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认领/线索申请表数据访问入口。
 */
@Mapper
public interface ClaimApplicationMapper extends BaseMapper<ClaimApplication> {
}
