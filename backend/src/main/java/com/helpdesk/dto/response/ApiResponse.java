package com.helpdesk.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wrapper chuẩn cho tất cả API response.
 * success: true/false
 * message: mô tả kết quả
 * data: payload (null khi lỗi)
 * errors: chi tiết lỗi (null khi thành công)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    Object errors
) {

    /** Thành công với dữ liệu. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Thành công", data, null);
    }

    /** Thành công với message tùy chỉnh. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    /** Thành công không có dữ liệu. */
    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    /** Lỗi nghiệp vụ. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    /** Lỗi validation chi tiết. */
    public static <T> ApiResponse<T> error(String message, Object errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}
