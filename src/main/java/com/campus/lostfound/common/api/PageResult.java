package com.campus.lostfound.common.api;

import java.util.List;

/**
 * 统一分页结果，与 MyBatis-Plus 的 Page 对象解耦，避免持久层类型泄露到 API。
 */
public record PageResult<T>(
        List<T> records,
        long current,
        long size,
        long total
) {
    public PageResult {
        // records 可能是可变集合，通过不可变副本防止调用方意外修改响应数据。
        records = List.copyOf(records);
    }
}

