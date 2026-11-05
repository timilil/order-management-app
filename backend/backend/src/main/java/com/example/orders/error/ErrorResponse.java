package com.example.orders.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The one JSON error body used by every error response, including the 401 and 403 produced by Spring
 * Security. {@code error} is a stable code clients can branch on, {@code message} is for humans and never
 * contains internal details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status, String error, String message, Instant timestamp, String path, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message, Instant timestamp, String path) {
        return new ErrorResponse(status, error, message, timestamp, path, null);
    }
}
