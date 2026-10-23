package com.campus.lostfound.common.api;

import java.util.List;

public record PageResult<T>(
        List<T> records,
        long current,
        long size,
        long total
) {
    public PageResult {
        records = List.copyOf(records);
    }
}

