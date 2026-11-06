package com.neonvibe.ytdlp;

/**
 * Failure while running the {@code yt-dlp} process: missing binary, timeout,
 * interrupted execution or a non-zero exit code. The message includes the tail
 * of the process output for logging; user-facing messages are mapped by
 * {@link YouTubeImportService}.
 */
public class YtDlpException extends RuntimeException {

    public YtDlpException(String message) {
        super(message);
    }

    public YtDlpException(String message, Throwable cause) {
        super(message, cause);
    }
}
