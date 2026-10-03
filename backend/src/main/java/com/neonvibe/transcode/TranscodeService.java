package com.neonvibe.transcode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import com.neonvibe.domain.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * On-demand, cached audio transcoding to AAC.
 *
 * <p>Given a track and a requested {@link Quality}, decides whether transcoding
 * is worthwhile (source is lossless or higher-bitrate than the target), and if so
 * produces/caches an AAC file under {@code neonvibe.transcode.cache-path}. The
 * original file is never modified. When FFmpeg is unavailable or transcoding
 * fails, callers fall back to the original file.</p>
 */
@Service
public class TranscodeService {

    private static final Logger log = LoggerFactory.getLogger(TranscodeService.class);

    private static final Set<String> LOSSLESS = Set.of("flac", "wav", "alac", "aiff", "ape");

    private final TranscodeProperties properties;
    private final FfmpegExecutor executor;
    private final Semaphore slots;

    private volatile Boolean available;

    public TranscodeService(TranscodeProperties properties, FfmpegExecutor executor) {
        this.properties = properties;
        this.executor = executor;
        this.slots = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
    }

    /**
     * Result of resolving a requested quality: the file to serve and whether it
     * is a transcoded copy (AAC) rather than the original.
     */
    public record Resolved(Path path, boolean transcoded) {
    }

    /** Whether transcoding is enabled and FFmpeg is usable (cached after first check). */
    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            return false;
        }
        Boolean cached = available;
        if (cached == null) {
            cached = executor.isAvailable(properties.getFfmpegPath());
            available = cached;
        }
        return cached;
    }

    /** Resolves the file to serve for {@code quality}, transcoding/caching if needed. */
    public Resolved resolve(Track track, Quality quality) {
        Path original = Path.of(track.getFilePath());
        if (quality == null || quality == Quality.ORIGINAL
                || !isAvailable() || !needsTranscode(track, quality)) {
            return new Resolved(original, false);
        }

        Path cached = cachedPath(track, quality);
        try {
            if (!Files.isRegularFile(cached)) {
                generate(track, quality, cached);
            }
            return new Resolved(cached, true);
        } catch (Exception ex) {
            log.warn("Transcode failed for track {} at {}; serving original: {}",
                    track.getId(), quality.param(), ex.getMessage());
            return new Resolved(original, false);
        }
    }

    /**
     * Whether transcoding is worthwhile: always for lossless sources, and for
     * lossy sources only when their known bitrate exceeds the target (otherwise
     * the original already satisfies the requested quality).
     */
    boolean needsTranscode(Track track, Quality quality) {
        if (quality == null || quality == Quality.ORIGINAL) {
            return false;
        }
        String format = track.getFormat() == null ? "" : track.getFormat().toLowerCase(Locale.ROOT);
        if (LOSSLESS.contains(format)) {
            return true;
        }
        Integer bitrate = track.getBitrate();
        return bitrate != null && bitrate > 0 && bitrate > quality.targetKbps();
    }

    /** Cache file for a track+quality, e.g. {@code {id}-{quality}.m4a}. */
    Path cachedPath(Track track, Quality quality) {
        return Path.of(properties.getCachePath())
                .resolve(track.getId() + "-" + quality.param() + ".m4a");
    }

    private void generate(Track track, Quality quality, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part-" + UUID.randomUUID());
        List<String> command = List.of(
                properties.getFfmpegPath(), "-y", "-hide_banner", "-loglevel", "error",
                "-i", track.getFilePath(),
                "-vn", "-map_metadata", "-1",
                "-c:a", "aac", "-b:a", quality.targetKbps() + "k",
                "-movflags", "+faststart",
                "-f", "mp4",
                tmp.toString());
        try {
            slots.acquire();
            try {
                executor.transcode(command, Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())));
                if (!Files.isRegularFile(tmp)) {
                    throw new IOException("FFmpeg produced no output");
                }
                move(tmp, target);
            } finally {
                slots.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while transcoding", ex);
        } finally {
            Files.deleteIfExists(tmp);
        }
        evictIfNeeded();
    }

    private void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes the oldest cache files while the total exceeds the configured cap. */
    private void evictIfNeeded() {
        long maxBytes = Math.max(0, properties.getMaxCacheMb()) * 1024 * 1024;
        Path dir = Path.of(properties.getCachePath());
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> ordered = files
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(TranscodeService::lastModified))
                    .toList();
            long total = ordered.stream().mapToLong(TranscodeService::size).sum();
            for (Path file : ordered) {
                if (total <= maxBytes) {
                    break;
                }
                long length = size(file);
                if (Files.deleteIfExists(file)) {
                    total -= length;
                }
            }
        } catch (IOException ex) {
            log.debug("Transcode cache eviction failed: {}", ex.getMessage());
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return 0L;
        }
    }
}
