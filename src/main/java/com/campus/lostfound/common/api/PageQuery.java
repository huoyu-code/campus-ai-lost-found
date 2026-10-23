package com.campus.lostfound.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageQuery {

    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    private Integer size = 10;
}

