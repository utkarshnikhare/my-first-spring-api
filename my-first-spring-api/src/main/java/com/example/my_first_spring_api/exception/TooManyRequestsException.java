package com.example.my_first_spring_api.exception;

/**
 * Thrown when a login or registration rate limit is exceeded. Mapped by
 * {@code GlobalExceptionHandler} to HTTP 429 with a clear, non-sensitive message.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException() {
        super("Too many attempts. Please wait a few minutes and try again.");
    }
}
