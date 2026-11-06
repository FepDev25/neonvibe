package com.neonvibe.scanner;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Short-lived registry of files the application itself just wrote (metadata
 * edits). The filesystem watcher would otherwise see the tag write as an
 * external change and re-ingest the file at the same time as the edit
 * transaction, racing on the same artist/album/track rows.
 */
@Component
public class RecentWrites {

    /** A watcher event for a self-written file arrives almost immediately; 30s is ample. */
    private static final long TTL_MS = 30_000;

    private final Map<Path, Long> writes = new ConcurrentHashMap<>();

    public void mark(Path path) {
        if (path != null) {
            writes.put(normalize(path), System.currentTimeMillis());
        }
    }

    public boolean isRecent(Path path) {
        if (path == null) {
            return false;
        }
        Path key = normalize(path);
        Long markedAt = writes.get(key);
        if (markedAt == null) {
            return false;
        }
        if (System.currentTimeMillis() - markedAt > TTL_MS) {
            writes.remove(key);
            return false;
        }
        return true;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
