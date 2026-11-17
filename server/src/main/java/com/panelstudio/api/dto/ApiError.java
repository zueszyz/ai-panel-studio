package com.panelstudio.api.dto;

import java.util.Map;

/**
 * 统一错误体：{@code {"error":{"code","message","details"}}}。
 * code 取值与 docs/API.md §4 一致。
 */
public record ApiError(ErrorBody error) {

    public record ErrorBody(String code, String message, Object details) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(new ErrorBody(code, message, null));
    }

    public static ApiError of(String code, String message, Object details) {
        return new ApiError(new ErrorBody(code, message, details));
    }

    public static ApiError of(String code, String message, Map<String, Object> details) {
        return new ApiError(new ErrorBody(code, message, details));
    }
}
