package com.neonvibe.scanner;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code neonvibe.music.*} configuration keys.
 */
@ConfigurationProperties(prefix = "neonvibe.music")
public class ScannerConfig {

    /**
     * Root directories to watch/scan. Defaults to the production path `/srv/Music`;
     * the dev profile overrides this with a local test path.
     */
    private List<String> paths = List.of("/srv/Music");

    /**
     * Comma-separated supported file extensions (without leading dot).
     */
    private List<String> supportedFormats = List.of("mp3", "flac", "aac", "ogg", "m4a", "wav");

    /**
     * Interval (seconds) for the periodic fallback scan. 0 disables period scanning
     * (WatchService + initial scan + manual triggers only).
     */
    private long scanIntervalSeconds = 0;

    /**
     * Staging directory for web uploads (files/ZIPs land here, are validated and
     * moved into the library). Kept OUTSIDE the watched music roots so the
     * watcher does not ingest half-written uploads. Blank derives
     * {@code <java.io.tmpdir>/neonvibe-staging}.
     */
    private String stagingPath = "";

    /**
     * Drop folder inside the library (watched): anything copied here via
     * SFTP/rsync is organized into {@code <Artist>/<Album>/} and ingested.
     * Blank derives {@code <first root>/incoming}.
     */
    private String incomingPath = "";

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public List<String> getSupportedFormats() {
        return supportedFormats;
    }

    public void setSupportedFormats(List<String> supportedFormats) {
        this.supportedFormats = supportedFormats;
    }

    public long getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    public void setScanIntervalSeconds(long scanIntervalSeconds) {
        this.scanIntervalSeconds = scanIntervalSeconds;
    }

    public String getStagingPath() {
        return stagingPath;
    }

    public void setStagingPath(String stagingPath) {
        this.stagingPath = stagingPath;
    }

    public String getIncomingPath() {
        return incomingPath;
    }

    public void setIncomingPath(String incomingPath) {
        this.incomingPath = incomingPath;
    }

    /** First configured music root (destination for organized uploads). */
    public Path primaryRoot() {
        return paths == null || paths.isEmpty() ? Path.of("/srv/Music") : Path.of(paths.get(0));
    }

    /** Resolved staging directory (uploaded files are written here first). */
    public Path resolveStagingPath() {
        if (stagingPath != null && !stagingPath.isBlank()) {
            return Path.of(stagingPath);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "neonvibe-staging");
    }

    /** Resolved drop folder inside the library. */
    public Path resolveIncomingPath() {
        if (incomingPath != null && !incomingPath.isBlank()) {
            return Path.of(incomingPath);
        }
        return primaryRoot().resolve("incoming");
    }

    public boolean isFormatSupported(String extension) {
        return extension != null && supportedFormats.stream()
                .anyMatch(f -> f.equalsIgnoreCase(extension.replaceFirst("^\\.", "")));
    }
}
