package com.neonvibe.exception;

/**
 * Thrown when a request conflicts with the current state of a resource (for
 * example, creating a favorite that already exists). Maps to HTTP 409.
 *
 * <p>Extends {@link IllegalStateException} so callers/tests that treated it as a
 * generic illegal state keep working, while the global handler maps it to a
 * precise {@code 409 Conflict} instead of {@code 400 Bad Request}.</p>
 */
public class ConflictException extends IllegalStateException {

    public ConflictException(String message) {
        super(message);
    }
}
