package com.neonvibe.ytdlp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs {@code yt-dlp} as a subprocess. Output is drained on a daemon thread
 * (a chatty download cannot fill the pipe and block the process) and only a
 * bounded tail is kept to build error messages.
 */
@Component
public class DefaultYtDlpExecutor implements YtDlpExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultYtDlpExecutor.class);

    /** Probes ({@code --version}) are fast; never let a hung binary block a request. */
    private static final int PROBE_TIMEOUT_SECONDS = 10;

    /** Keep only the tail of the output for error messages (and logs). */
    private static final int MAX_OUTPUT_CHARS = 8000;

    private final YtDlpProperties properties;

    public DefaultYtDlpExecutor(YtDlpProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> version() {
        try {
            ExecResult result = execute(
                    List.of(properties.getBinary(), "--version"),
                    Duration.ofSeconds(PROBE_TIMEOUT_SECONDS));
            if (result.exitCode() != 0) {
                return Optional.empty();
            }
            return result.output().lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .findFirst();
        } catch (Exception ex) {
            log.debug("yt-dlp not available at '{}': {}", properties.getBinary(), ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean runtimeAvailable(String runtime) {
        if (runtime == null || runtime.isBlank()) {
            return true;
        }
        try {
            return execute(List.of(runtime, "--version"),
                    Duration.ofSeconds(PROBE_TIMEOUT_SECONDS)).exitCode() == 0;
        } catch (Exception ex) {
            log.debug("JavaScript runtime '{}' not available: {}", runtime, ex.getMessage());
            return false;
        }
    }

    @Override
    public void run(List<String> command, Duration timeout) {
        ExecResult result;
        try {
            result = execute(command, timeout);
        } catch (IOException ex) {
            throw new YtDlpException(
                    "Could not run " + command.get(0) + ": " + ex.getMessage(), ex);
        }
        if (result.exitCode() != 0) {
            throw new YtDlpException("yt-dlp exited with code " + result.exitCode()
                    + ": " + tail(result.output()));
        }
    }

    private ExecResult execute(List<String> command, Duration timeout) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        // systemd runs the service with ProtectHome=true: Deno cannot use
        // $HOME/.cache. Point its cache at the (writable) download staging.
        String denoDir = builder.environment().get("DENO_DIR");
        if (denoDir == null || denoDir.isBlank()) {
            builder.environment().put("DENO_DIR",
                    properties.resolveStagingPath().resolve("deno").toString());
        }
        Process process = builder.start();
        OutputCollector collector = new OutputCollector();
        Thread drainer = new Thread(() -> collector.drain(process.getInputStream()), "ytdlp-drain");
        drainer.setDaemon(true);
        drainer.start();

        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, timeout.toSeconds()), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new YtDlpException("Interrupted while running yt-dlp", ex);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new YtDlpException("yt-dlp timed out after " + timeout.toSeconds() + "s");
        }
        joinQuietly(drainer);
        return new ExecResult(process.exitValue(), collector.text());
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String tail(String output) {
        String trimmed = output == null ? "" : output.trim();
        return trimmed.length() <= 2000 ? trimmed : trimmed.substring(trimmed.length() - 2000);
    }

    private record ExecResult(int exitCode, String output) {
    }

    /** Thread-safe, bounded output buffer. */
    private static final class OutputCollector {

        private final StringBuilder buffer = new StringBuilder();

        void drain(InputStream input) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (this) {
                        if (buffer.length() > MAX_OUTPUT_CHARS) {
                            buffer.delete(0, buffer.length() - MAX_OUTPUT_CHARS);
                        }
                        buffer.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // Process ended or was destroyed; whatever was captured is enough.
            }
        }

        synchronized String text() {
            return buffer.toString();
        }
    }
}
