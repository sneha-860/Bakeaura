package com.bakeaura.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;        // T = any type (User, Product, List, etc.)

    // Factory methods for convenience
    public static <T> ApiResponse<T> ok(String msg, T data) {
        return new ApiResponse<>(true, msg, data);
    }
    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(false, msg, null);
    }
}

// Every API now returns:
// { "success": true, "message": "Product created", "data": { ... } }
// { "success": false, "message": "Not authorized", "data": null }