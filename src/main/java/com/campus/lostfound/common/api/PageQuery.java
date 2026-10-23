package com.campus.lostfound.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询基类。
 *
 * <p>所有列表查询都必须继承此类型，确保页码合法且单页最多返回 50 条。</p>
 */
@Getter
@Setter
public class PageQuery {

    /**
     * 页码上限用于约束 OFFSET，避免深分页扫描过多数据。
     * 单页最多 50 条，因此当前可访问的最大偏移量为 49,950 条。
     */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于 1")
    @Max(value = 1000, message = "页码不能超过 1000")
    private Integer page = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    private Integer size = 10;
}

