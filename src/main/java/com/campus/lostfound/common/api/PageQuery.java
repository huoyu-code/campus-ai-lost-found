package com.campus.lostfound.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    private Integer size = 10;
}

