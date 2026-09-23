package com.neonvibe.scanner;

/**
 * Signals a serious scanner-level failure (e.g. an inaccessible configured root).
 * Individual file errors are handled per-file and logged; this exception is only
 * used for non-recoverable orchestration problems.
 */
public class ScanException extends RuntimeException {

    public ScanException(String message) {
        super(message);
    }

    public ScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
