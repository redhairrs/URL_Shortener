package com.urlshortener.exception;

/**
 * Thrown when the submitted URL is malformed or uses a disallowed protocol.
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String url) {
        super("Invalid URL: " + url);
    }
}
