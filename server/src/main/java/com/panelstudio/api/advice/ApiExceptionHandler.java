package com.panelstudio.api.advice;

import com.panelstudio.api.dto.ApiError;
import com.panelstudio.domain.statemachine.IllegalStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局异常出口：所有错误都变成 {@code {"error":{"code","message","details"}}}。
 * 错误码与 docs/API.md §4 对齐；未预期异常统一 500 INTERNAL_ERROR，且不回传堆栈。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        if (ex.status().is5xxServerError()) {
            log.error("业务异常: code={} message={}", ex.code(), ex.getMessage(), ex);
        } else {
            log.debug("业务异常: code={} message={}", ex.code(), ex.getMessage());
        }
        return ResponseEntity.status(ex.status())
                .body(ApiError.of(ex.code(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", fieldError.getField());
            item.put("message", fieldError.getDefaultMessage());
            details.add(item);
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_ERROR", "参数校验未通过", details));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> handleMissingParam(Exception ex) {
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("field", ex instanceof MissingRequestHeaderException header
                ? header.getHeaderName()
                : ((MissingServletRequestParameterException) ex).getParameterName());
        item.put("message", "缺少必填参数");
        details.add(item);
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_ERROR", "参数校验未通过", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("请求体无法解析: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_ERROR", "请求体不是合法 JSON"));
    }

    /**
     * 并发认领同一席位时，数据库的部分唯一索引会抛 DuplicateKey：
     * 这是业务上的「席位已被认领」，不是服务端故障。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(DuplicateKeyException ex) {
        log.debug("唯一约束冲突: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("SEAT_TAKEN", "该席位已被其他参与者认领"));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ApiError> handleIllegalTransition(IllegalStateTransitionException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", String.valueOf(ex.from()));
        details.put("to", String.valueOf(ex.to()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("DISCUSSION_STATE_INVALID", "当前状态不允许该操作", details));
    }

    /**
     * 客户端断开 SSE 连接：不是服务端故障，只记 debug，避免刷错误日志，也不影响正在进行的生成。
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, java.io.IOException.class})
    public void handleClientGone(Exception ex) {
        // 客户端断开 SSE 后写入 socket 会抛 IOException：不是服务端故障，只记 debug。
        log.debug("客户端已断开: {}", ex.getMessage());
    }

    /**
     * SSE 响应一旦提交，Content-Type 已是 text/event-stream，无法再写 JSON 错误体。
     * 此时只记 debug 并结束，避免 HttpMessageNotWritableException 引发连锁 ERROR 日志。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex,
                                                     jakarta.servlet.http.HttpServletResponse response) {
        String contentType = String.valueOf(response.getContentType());
        if (response.isCommitted() || contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            log.debug("响应已提交，忽略后续异常: {}", ex.toString());
            return null;
        }
        log.error("未预期异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "服务内部错误，请稍后重试"));
    }
}
