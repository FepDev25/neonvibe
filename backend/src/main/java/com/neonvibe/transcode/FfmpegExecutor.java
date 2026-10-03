package com.neonvibe.transcode;

import java.time.Duration;
import java.util.List;

/**
 * Abstraction over the FFmpeg process so {@link TranscodeService} can be unit
 * tested without a real binary on the machine.
 */
public interface FfmpegExecutor {

    /** Whether the given FFmpeg binary responds to {@code -version}. */
    boolean isAvailable(String ffmpegPath);

    /**
     * Runs the given FFmpeg command to completion, aborting after {@code timeout}.
     *
     * @throws IllegalStateException on a non-zero exit, timeout or IO error
     */
    void transcode(List<String> command, Duration timeout);
}
