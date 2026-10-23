package com.campus.lostfound.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.lostfound.post.entity.PostImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子图片 URL 表数据访问入口。
 */
@Mapper
public interface PostImageMapper extends BaseMapper<PostImage> {
}

