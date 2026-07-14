package com.urlshortener.exception;

/**
 * Thrown when a short code cannot be found in the database.
 */
public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String shortCode) {
        super("Short code not found: " + shortCode);
    }
}
