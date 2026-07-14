package com.urlshortener.exception;

/**
 * Thrown when a short URL has passed its expiration time.
 * Results in HTTP 410 Gone.
 */
public class UrlExpiredException extends RuntimeException {

    public UrlExpiredException(String shortCode) {
        super("Short URL has expired: " + shortCode);
    }
}
