package com.urlshortener.exception;

/**
 * Thrown when a client exceeds their rate limit.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Rate limit exceeded. Please try again later.");
    }
}
