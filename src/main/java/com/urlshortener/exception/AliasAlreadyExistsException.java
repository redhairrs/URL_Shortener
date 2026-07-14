package com.urlshortener.exception;

/**
 * Thrown when a requested custom alias is already taken.
 */
public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String alias) {
        super("Custom alias already exists: " + alias);
    }
}
