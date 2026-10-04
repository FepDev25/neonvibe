package com.neonvibe.exception;

/**
 * Raised when audio tags could not be written to the underlying file (read-only
 * filesystem, unsupported format, corrupt file, ...). Distinct from generic
 * failures so the API can return a meaningful status to the client.
 */
public class TagWriteException extends RuntimeException {

    public TagWriteException(String message) {
        super(message);
    }

    public TagWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
