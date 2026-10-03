package com.neonvibe.transcode;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs FFmpeg as a subprocess, draining its output on a daemon thread so a
 * chatty/large log cannot fill the pipe buffer and block the process.
 */
@Component
public class DefaultFfmpegExecutor implements FfmpegExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultFfmpegExecutor.class);

    @Override
    public boolean isAvailable(String ffmpegPath) {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            drain(process);
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ex) {
            log.info("FFmpeg not available at '{}': {}", ffmpegPath, ex.getMessage());
            return false;
        }
    }

    @Override
    public void transcode(List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            drain(process);
            if (!process.waitFor(Math.max(1, timeout.toSeconds()), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("FFmpeg timed out after " + timeout.toSeconds() + "s");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("FFmpeg exited with code " + process.exitValue());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not run FFmpeg: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running FFmpeg", ex);
        }
    }

    /** Discards stdout/stderr on a daemon thread to keep the pipe from blocking. */
    private void drain(Process process) {
        Thread drainer = new Thread(() -> {
            try (var in = process.getInputStream()) {
                in.transferTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // process ended; nothing to drain
            }
        }, "ffmpeg-drain");
        drainer.setDaemon(true);
        drainer.start();
    }
}
