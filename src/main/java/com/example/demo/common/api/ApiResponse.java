package com.example.demo.common.api;

import java.time.Instant;

/**
 * Generic envelope for every REST payload so clients get a uniform shape
 * regardless of endpoint or outcome. Immutable record — built only on the way
 * out (never deserialized from a request), so the {@link Instant} timestamp and
 * the generic {@code data} serialize cleanly via the web {@code ObjectMapper}.
 *
 * <p>Construct through the static factories rather than the canonical
 * constructor so the {@code success} flag and {@code timestamp} stay consistent.
 *
 * @param <T> payload type carried in {@code data}
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp) {

    /** Successful response with an explicit human-readable message. */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    /** Successful response with a default message. */
    public static <T> ApiResponse<T> success(T data) {
        return success("OK", data);
    }

    /** Failure response carrying no data. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
