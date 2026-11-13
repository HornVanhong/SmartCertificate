package com.smartcertificate.common.api;

import java.util.Map;

public record ApiErrorResponse(ApiError error) {

    public static ApiErrorResponse of(String code, String message, String requestId) {
        return new ApiErrorResponse(new ApiError(code, message, Map.of(), requestId));
    }

    public static ApiErrorResponse of(String code, String message, Map<String, String> fieldErrors, String requestId) {
        return new ApiErrorResponse(new ApiError(code, message, fieldErrors, requestId));
    }
}
