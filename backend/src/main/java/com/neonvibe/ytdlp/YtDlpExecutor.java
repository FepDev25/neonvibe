package com.neonvibe.ytdlp;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Thin abstraction over the {@code yt-dlp} subprocess so the import service can
 * be unit-tested without downloading from YouTube.
 */
public interface YtDlpExecutor {

    /**
     * @return the reported version, or empty when the binary is missing or not
     *         executable by the server user
     */
    Optional<String> version();

    /**
     * @param runtime executable name (e.g. {@code deno}); blank means "not
     *                configured" and is considered available
     * @return whether the JavaScript runtime can be executed
     */
    boolean runtimeAvailable(String runtime);

    /**
     * Runs a full yt-dlp command and waits for it.
     *
     * @throws YtDlpException on timeout, interruption or a non-zero exit code
     */
    void run(List<String> command, Duration timeout);
}
