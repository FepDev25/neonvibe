package com.neonvibe.dto;

import java.time.Instant;
import java.util.Map;

import com.neonvibe.scanner.ScannerStatus.State;

/**
 * Read-only snapshot of the scanner, backing {@code GET /admin/scan/status}.
 */
public record ScanStatusResponse(
        State state,
        long totalScanned,
        long processed,
        long failed,
        boolean running,
        Instant startedAt,
        Instant finishedAt,
        Map<String, String> failedFiles) {

    /** Static factory from the scanner status object to avoid coupling DTO to model. */
    public static ScanStatusResponse fromState(
            State state, long totalScanned, long processed, long failed,
            boolean running, Instant startedAt, Instant finishedAt,
            Map<String, String> failedFiles) {
        return new ScanStatusResponse(state, totalScanned, processed, failed,
                running, startedAt, finishedAt, failedFiles);
    }
}
