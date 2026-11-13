package com.smartcertificate.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(String requestId, PageMeta pagination) {

    public static ApiMeta of(String requestId) {
        return new ApiMeta(requestId, null);
    }

    public static ApiMeta of(String requestId, PageMeta pagination) {
        return new ApiMeta(requestId, pagination);
    }
}
