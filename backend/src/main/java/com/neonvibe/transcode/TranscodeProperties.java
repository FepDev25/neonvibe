package com.neonvibe.transcode;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code neonvibe.transcode.*} configuration keys. When disabled or
 * when FFmpeg is not installed, streams always fall back to the original file.
 */
@ConfigurationProperties(prefix = "neonvibe.transcode")
public class TranscodeProperties {

    /** Master switch for on-demand transcoding. */
    private boolean enabled = true;

    /** FFmpeg executable (PATH lookup or absolute path). */
    private String ffmpegPath = "ffmpeg";

    /** Directory for the transcoded AAC cache (kept out of the music library). */
    private String cachePath = "./data/transcode";

    /** Cache size cap in MB; oldest files are evicted beyond it. */
    private long maxCacheMb = 2048;

    /** Maximum simultaneous FFmpeg processes. */
    private int maxConcurrent = 1;

    /** Per-process timeout in seconds. */
    private int timeoutSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getCachePath() {
        return cachePath;
    }

    public void setCachePath(String cachePath) {
        this.cachePath = cachePath;
    }

    public long getMaxCacheMb() {
        return maxCacheMb;
    }

    public void setMaxCacheMb(long maxCacheMb) {
        this.maxCacheMb = maxCacheMb;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
