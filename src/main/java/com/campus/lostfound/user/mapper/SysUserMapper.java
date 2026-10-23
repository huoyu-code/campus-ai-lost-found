package com.campus.lostfound.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表数据访问入口，通用 CRUD 由 MyBatis-Plus BaseMapper 提供。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

