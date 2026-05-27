package com.bakeaura.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;        // T = any type (User, Product, List, etc.)
    private String errorCode;
    private LocalDateTime timestamp;

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    // Factory methods for convenience
    public static <T> ApiResponse<T> ok(String msg, T data) {
        return new ApiResponse<>(true, msg, data);
    }
    public static <T> ApiResponse<T> error(String msg) {
        return error(msg, null);
    }
    public static <T> ApiResponse<T> error(String msg, String errorCode) {
        return new ApiResponse<>(false, msg, null, errorCode, LocalDateTime.now());
    }
}

// Every API now returns:
// { "success": true, "message": "Product created", "data": { ... } }
// { "success": false, "message": "Not authorized", "data": null }
