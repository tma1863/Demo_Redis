package com.example.demo.common.exception;

/**
 * Thrown when a requested domain resource cannot be found. Unchecked so it
 * propagates out of service/controller code without polluting signatures; it is
 * translated to an HTTP 404 (wrapped in the common {@code ApiResponse}) by
 * {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Convenience for the common "&lt;Entity&gt; not found with id: &lt;id&gt;" message.
     */
    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found with id: %s".formatted(resource, id));
    }
}
