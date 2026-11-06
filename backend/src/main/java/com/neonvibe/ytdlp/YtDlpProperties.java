package com.neonvibe.ytdlp;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code neonvibe.ytdlp.*} configuration keys.
 *
 * <p>Importing audio from YouTube is optional: when disabled or when the
 * {@code yt-dlp} binary cannot be executed, the endpoint reports it as
 * unavailable and the rest of the server keeps working normally.</p>
 */
@ConfigurationProperties(prefix = "neonvibe.ytdlp")
public class YtDlpProperties {

    /** Master switch for YouTube imports. */
    private boolean enabled = true;

    /** yt-dlp executable (PATH lookup or absolute path). */
    private String binary = "yt-dlp";

    /**
     * Optional FFmpeg location passed to yt-dlp. A bare name such as "ffmpeg"
     * means "resolve from PATH" (yt-dlp also needs ffprobe); only absolute paths
     * or paths containing "/" are passed as {@code --ffmpeg-location}.
     */
    private String ffmpegPath = "ffmpeg";

    /**
     * JavaScript runtime yt-dlp needs for full YouTube support since 2025.11.12
     * (Deno recommended). Blank => do not pass {@code --js-runtimes}.
     */
    private String jsRuntime = "deno";

    /** Container for the downloaded audio. */
    private String audioFormat = "mp3";

    /** Audio quality passed to yt-dlp (0 = best VBR for mp3). */
    private String audioQuality = "0";

    /** Optional cookies file for videos that require a signed-in session. */
    private String cookiesFile = "";

    /** Per-download timeout in seconds. */
    private int timeoutSeconds = 300;

    /** Reject videos longer than this (0 disables the filter). */
    private int maxDurationSeconds = 1800;

    /** Maximum simultaneous imports. */
    private int maxConcurrent = 1;

    /** Temp directory for downloads (blank => {@code <java.io.tmpdir>/neonvibe-ytdlp}). */
    private String stagingPath = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBinary() {
        return binary;
    }

    public void setBinary(String binary) {
        this.binary = binary;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getJsRuntime() {
        return jsRuntime;
    }

    public void setJsRuntime(String jsRuntime) {
        this.jsRuntime = jsRuntime;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public void setAudioFormat(String audioFormat) {
        this.audioFormat = audioFormat;
    }

    public String getAudioQuality() {
        return audioQuality;
    }

    public void setAudioQuality(String audioQuality) {
        this.audioQuality = audioQuality;
    }

    public String getCookiesFile() {
        return cookiesFile;
    }

    public void setCookiesFile(String cookiesFile) {
        this.cookiesFile = cookiesFile;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public String getStagingPath() {
        return stagingPath;
    }

    public void setStagingPath(String stagingPath) {
        this.stagingPath = stagingPath;
    }

    /** Resolved temp directory for in-flight downloads. */
    public Path resolveStagingPath() {
        if (stagingPath != null && !stagingPath.isBlank()) {
            return Path.of(stagingPath);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "neonvibe-ytdlp");
    }
}
