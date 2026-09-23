package com.neonvibe.scanner;

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

    public boolean isFormatSupported(String extension) {
        return extension != null && supportedFormats.stream()
                .anyMatch(f -> f.equalsIgnoreCase(extension.replaceFirst("^\\.", "")));
    }
}
