package com.neonvibe.websocket.dto;

/**
 * Payload broadcast by the server on {@code SCANNER_PROGRESS} to report library
 * scanning activity.
 *
 * @param scannedCount  number of paths processed so far
 * @param totalCount    total paths expected (0 when unknown)
 * @param status        human-readable scanner status (SCANNING / IDLE / ...)
 */
public record ScannerProgressMessage(int scannedCount, int totalCount, String status) {
}
