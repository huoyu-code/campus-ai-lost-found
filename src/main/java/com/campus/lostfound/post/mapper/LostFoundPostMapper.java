package com.campus.lostfound.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.post.entity.LostFoundPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 失物/招领帖子表数据访问入口。
 */
@Mapper
public interface LostFoundPostMapper extends BaseMapper<LostFoundPost> {
}

