package com.smartcertificate.common.api;

import com.smartcertificate.common.filter.RequestIdFilter;

public record ApiResponse<T>(T data, ApiMeta meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, ApiMeta.of(RequestIdFilter.currentRequestId()));
    }

    public static <T> ApiResponse<T> ok(T data, PageMeta pagination) {
        return new ApiResponse<>(data, ApiMeta.of(RequestIdFilter.currentRequestId(), pagination));
    }
}
