package com.example.my_first_spring_api.exception;

/**
 * Thrown when a login or registration rate limit is exceeded. Mapped by
 * {@code GlobalExceptionHandler} to HTTP 429 with a clear, non-sensitive message.
 */
public class TooManyRequestsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyRequestsException() {
        this(60);
    }

    public TooManyRequestsException(long retryAfterSeconds) {
        this("Too many failed attempts. Please try again in " + retryAfterSeconds
                + (retryAfterSeconds == 1 ? " second." : " seconds."), retryAfterSeconds);
    }

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
