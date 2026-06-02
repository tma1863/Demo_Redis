package com.example.demo.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.common.api.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Application-wide exception translation. Catches exceptions escaping any
 * {@code @RestController} and renders them through the common {@link ApiResponse}
 * envelope so error responses share the same shape as successful ones.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Domain "not found" -> 404. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Catch-all -> 500. The internal detail is logged but deliberately not echoed
     * to the client, so stack traces / internals never leak over the wire.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
