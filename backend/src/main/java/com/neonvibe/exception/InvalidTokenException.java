package com.neonvibe.exception;

/**
 * Thrown when an authentication token (JWT or Google id_token) is missing,
 * invalid or expired.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
