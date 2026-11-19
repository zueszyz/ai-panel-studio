package com.panelstudio.api.advice;

import com.panelstudio.api.dto.ApiError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** SSE 响应已提交时不能再写 JSON 错误体，否则会引发 HttpMessageNotWritable 连锁错误。 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("SSE 已提交：忽略异常，不写 ApiError")
    void shouldSkipErrorBodyForSseResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        assertThat(handler.handleUnexpected(new IOException("客户端断开"), response)).isNull();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("普通响应：仍返回统一的 500 INTERNAL_ERROR")
    void shouldStillWriteApiErrorForNormalResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ApiError> result = handler.handleUnexpected(new IllegalStateException("boom"), response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    }
}
