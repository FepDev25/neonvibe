package com.neonvibe.transcode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.neonvibe.domain.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TranscodeService}: eligibility heuristics, fallback to
 * the original file, and cache generation via a mocked FFmpeg executor.
 */
class TranscodeServiceTest {

    @TempDir
    Path tempDir;

    private TranscodeService service(FfmpegExecutor executor) {
        TranscodeProperties props = new TranscodeProperties();
        props.setCachePath(tempDir.resolve("cache").toString());
        props.setMaxCacheMb(100);
        return new TranscodeService(props, executor);
    }

    private FfmpegExecutor executor(boolean available) {
        return new FfmpegExecutor() {
            @Override
            public boolean isAvailable(String ffmpegPath) {
                return available;
            }

            @Override
            public void transcode(List<String> command, Duration timeout) {
                // no-op
            }
        };
    }

    private Track track(Long id, String format, Integer bitrate) {
        return Track.builder()
                .id(id)
                .filePath(tempDir.resolve("src-" + id + "." + format).toString())
                .format(format)
                .bitrate(bitrate)
                .build();
    }

    @Test
    void needsTranscode_losslessAlwaysTranscodes() {
        assertThat(service(executor(true)).needsTranscode(track(1L, "flac", 900), Quality.NORMAL)).isTrue();
    }

    @Test
    void needsTranscode_lossyAboveTargetTranscodes() {
        assertThat(service(executor(true)).needsTranscode(track(1L, "mp3", 320), Quality.NORMAL)).isTrue();
    }

    @Test
    void needsTranscode_lossyAtOrBelowTargetIsSkipped() {
        TranscodeService s = service(executor(true));
        assertThat(s.needsTranscode(track(1L, "mp3", 192), Quality.NORMAL)).isFalse();
        assertThat(s.needsTranscode(track(1L, "mp3", 128), Quality.NORMAL)).isFalse();
    }

    @Test
    void needsTranscode_unknownBitrateIsSkipped() {
        assertThat(service(executor(true)).needsTranscode(track(1L, "mp3", null), Quality.NORMAL)).isFalse();
    }

    @Test
    void needsTranscode_originalIsSkipped() {
        assertThat(service(executor(true)).needsTranscode(track(1L, "flac", 900), Quality.ORIGINAL)).isFalse();
    }

    @Test
    void resolve_originalReturnsSourceUntouched() {
        TranscodeService.Resolved resolved =
                service(executor(true)).resolve(track(1L, "flac", 900), Quality.ORIGINAL);

        assertThat(resolved.transcoded()).isFalse();
        assertThat(resolved.path().toString()).endsWith("src-1.flac");
    }

    @Test
    void resolve_ffmpegUnavailableFallsBackToOriginal() {
        TranscodeService.Resolved resolved =
                service(executor(false)).resolve(track(1L, "flac", 900), Quality.NORMAL);

        assertThat(resolved.transcoded()).isFalse();
        assertThat(resolved.path().toString()).endsWith("src-1.flac");
    }

    @Test
    void resolve_transcodesAndCachesAac() throws Exception {
        FfmpegExecutor writingExecutor = new FfmpegExecutor() {
            @Override
            public boolean isAvailable(String ffmpegPath) {
                return true;
            }

            @Override
            public void transcode(List<String> command, Duration timeout) {
                Path out = Path.of(command.get(command.size() - 1));
                try {
                    Files.createDirectories(out.getParent());
                    Files.writeString(out, "aac-bytes");
                    // The tmp file is renamed into place by the service; emulate the
                    // real output being written at the temp path (last argument).
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };

        TranscodeService.Resolved resolved =
                service(writingExecutor).resolve(track(1L, "flac", 900), Quality.NORMAL);

        assertThat(resolved.transcoded()).isTrue();
        assertThat(resolved.path().toString()).endsWith("1-normal.m4a");
        assertThat(Files.exists(resolved.path())).isTrue();
    }

    @Test
    void resolve_transcodeFailureFallsBackToOriginal() {
        FfmpegExecutor failing = new FfmpegExecutor() {
            @Override
            public boolean isAvailable(String ffmpegPath) {
                return true;
            }

            @Override
            public void transcode(List<String> command, Duration timeout) {
                throw new IllegalStateException("boom");
            }
        };

        TranscodeService.Resolved resolved = service(failing).resolve(track(1L, "flac", 900), Quality.NORMAL);

        assertThat(resolved.transcoded()).isFalse();
        assertThat(resolved.path().toString()).endsWith("src-1.flac");
    }
}
