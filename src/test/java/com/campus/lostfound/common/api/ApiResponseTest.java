package com.campus.lostfound.common.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一成功响应结构的基础单元测试。
 */
class ApiResponseTest {

    @Test
    void createsSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("ok", "trace-1");

        assertThat(response.code()).isEqualTo(ApiCode.SUCCESS);
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.traceId()).isEqualTo("trace-1");
    }
}

