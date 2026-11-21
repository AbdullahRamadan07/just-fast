package com.justfast.common.error;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned by every API endpoint. */
public record ApiError(Instant timestamp, int status, String code, String message, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, null);
    }

    public static ApiError withFields(int status, String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, fieldErrors);
    }
}
