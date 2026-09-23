package com.neonvibe.scanner;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory snapshot of the scanner's activity. Populated by
 * {@link MusicScannerService} and read by {@code GET /admin/scan/status}.
 */
@Component
public class ScannerStatus {

    public enum State {
        IDLE,
        SCANNING
    }

    private volatile State state = State.IDLE;
    private final java.util.concurrent.atomic.AtomicLong totalScanned = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong processed = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong failed = new java.util.concurrent.atomic.AtomicLong();
    private final Map<String, String> failedFiles = new ConcurrentHashMap<>();
    private volatile Instant startedAt;
    private volatile Instant finishedAt;

    public void markScanning(Instant start) {
        this.state = State.SCANNING;
        this.startedAt = start;
        this.finishedAt = null;
        this.failedFiles.clear();
        this.totalScanned.set(0);
        this.processed.set(0);
        this.failed.set(0);
    }

    public void markIdle(Instant finish) {
        this.state = State.IDLE;
        this.finishedAt = finish;
    }

    public void incScanned() {
        this.totalScanned.incrementAndGet();
    }

    public void incProcessed() {
        this.processed.incrementAndGet();
    }

    public void incFailed(String path, String reason) {
        this.failed.incrementAndGet();
        this.failedFiles.put(path, reason);
    }

    public State getState() {
        return state;
    }

    public long getTotalScanned() {
        return totalScanned.get();
    }

    public long getProcessed() {
        return processed.get();
    }

    public long getFailed() {
        return failed.get();
    }

    public boolean isRunning() {
        return state == State.SCANNING;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Map<String, String> getFailedFiles() {
        return Collections.unmodifiableMap(failedFiles);
    }
}
