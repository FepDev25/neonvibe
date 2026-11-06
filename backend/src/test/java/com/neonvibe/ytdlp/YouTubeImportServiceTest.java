package com.neonvibe.ytdlp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neonvibe.domain.Album;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.YouTubeImportJobResponse;
import com.neonvibe.dto.YouTubeStatusResponse;
import com.neonvibe.exception.ConflictException;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.scanner.AudioTagWriter;
import com.neonvibe.scanner.IngestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeImportServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private YtDlpExecutor executor;
    @Mock
    private IngestService ingestService;
    @Mock
    private AudioTagWriter tagWriter;

    private YtDlpProperties properties;
    private ExecutorService jobExecutor;
    private YouTubeImportService service;

    @BeforeEach
    void setUp() {
        properties = new YtDlpProperties();
        properties.setStagingPath(tempDir.toString());
        properties.setJsRuntime("deno");
        jobExecutor = Executors.newSingleThreadExecutor();
        service = new YouTubeImportService(properties, executor, ingestService, tagWriter,
                new ObjectMapper(), jobExecutor);
    }

    @AfterEach
    void tearDown() {
        jobExecutor.shutdownNow();
    }

    @Test
    void start_downloadsTagsAndIngestsIntoSingles() throws Exception {
        when(executor.version()).thenReturn(Optional.of("2026.03.17"));
        doAnswer(invocation -> {
            List<String> command = invocation.getArgument(0);
            Path dir = Path.of(command.get(command.indexOf("-o") + 1)).getParent();
            Files.writeString(dir.resolve("abc123.mp3"), "audio");
            Files.writeString(dir.resolve("abc123.info.json"), """
                    {"track":"Song","title":"Song (Official Video)","artist":"Artist",
                     "upload_date":"20240115","duration":200}
                    """);
            Files.write(dir.resolve("abc123.jpg"), new byte[]{1, 2, 3});
            return null;
        }).when(executor).run(any(), any());
        when(ingestService.ingestSingle(any())).thenAnswer(invocation -> Track.builder()
                .id(77L)
                .filePath(invocation.<Path>getArgument(0).toString())
                .albumEntity(Album.builder().id(9L).name("Singles").build())
                .build());

        YouTubeImportJobResponse started = service.start("https://www.youtube.com/watch?v=abc123");
        YouTubeImportJobResponse finished = awaitTerminal(started.jobId());

        assertThat(finished.state()).isEqualTo("SUCCEEDED");
        assertThat(finished.title()).isEqualTo("Song");
        assertThat(finished.artist()).isEqualTo("Artist");
        assertThat(finished.trackId()).isEqualTo(77L);
        assertThat(finished.albumId()).isEqualTo(9L);

        verify(tagWriter).write(any(Path.class), eq(new AudioTagWriter.TagValues(
                "Song", "Artist", "Singles", null, 2024, null, null, null)));
        verify(tagWriter).writeArtwork(any(Path.class), eq(new byte[]{1, 2, 3}), eq("image/jpeg"));

        ArgumentCaptor<Path> ingested = ArgumentCaptor.forClass(Path.class);
        verify(ingestService).ingestSingle(ingested.capture());
        assertThat(ingested.getValue().getFileName().toString()).isEqualTo("Artist - Song.mp3");
    }

    @Test
    void start_reportsUnavailableVideoAsAFailedJob() throws Exception {
        when(executor.version()).thenReturn(Optional.of("2026.03.17"));
        doThrow(new YtDlpException("ERROR: [youtube] abc: Video unavailable"))
                .when(executor).run(any(), any());

        YouTubeImportJobResponse started = service.start("https://youtu.be/abc123");
        YouTubeImportJobResponse finished = awaitTerminal(started.jobId());

        assertThat(finished.state()).isEqualTo("FAILED");
        assertThat(finished.error()).contains("no está disponible");
    }

    @Test
    void start_isRejectedWhenDisabledOrWithoutYtDlp() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> service.start("https://youtu.be/abc123"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deshabilitada");
    }

    @Test
    void start_isRejectedWhenTheBinaryIsMissing() {
        when(executor.version()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start("https://youtu.be/abc123"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("yt-dlp");
    }

    @Test
    void start_rejectsNonYouTubeUrls() {
        assertThatThrownBy(() -> service.start("https://example.com/watch?v=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void job_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> service.job("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buildCommand_usesSafetyFlagsAndOptionalInputs() throws IOException {
        Path cookies = tempDir.resolve("cookies.txt");
        Files.writeString(cookies, "cookie");
        properties.setCookiesFile(cookies.toString());

        List<String> command = service.buildCommand("https://youtu.be/abc123", tempDir);

        assertThat(command).contains(
                "--no-config", "--no-cache-dir", "--no-playlist", "--no-progress",
                "--write-info-json", "--write-thumbnail", "-x",
                "--audio-format", "mp3", "--js-runtimes", "deno", "--cookies");
        assertThat(command).contains("duration <= 1800");
        assertThat(command).contains("-o", tempDir.resolve("%(id)s.%(ext)s").toString());
        assertThat(command.get(command.size() - 1)).isEqualTo("https://youtu.be/abc123");
        // A bare "ffmpeg" must not be passed as a location: yt-dlp needs to
        // resolve both ffmpeg and ffprobe from PATH.
        assertThat(command).doesNotContain("--ffmpeg-location");
    }

    @Test
    void buildCommand_passesAnExplicitFfmpegLocationOnlyForRealPaths() {
        properties.setFfmpegPath("/usr/bin/ffmpeg");

        List<String> command = service.buildCommand("https://youtu.be/abc123", tempDir);

        assertThat(command).contains("--ffmpeg-location", "/usr/bin/ffmpeg");
    }

    @Test
    void buildCommand_omitsCookiesWhenTheFileDoesNotExist() {
        properties.setCookiesFile(tempDir.resolve("missing.txt").toString());

        assertThat(service.buildCommand("https://youtu.be/abc123", tempDir))
                .doesNotContain("--cookies");
    }

    @Test
    void status_reportsVersionAndRuntime() {
        when(executor.version()).thenReturn(Optional.of("2026.03.17"));
        when(executor.runtimeAvailable("deno")).thenReturn(true);

        YouTubeStatusResponse status = service.status();

        assertThat(status.available()).isTrue();
        assertThat(status.version()).isEqualTo("2026.03.17");
        assertThat(status.jsRuntimeAvailable()).isTrue();
        assertThat(status.message()).isNull();
    }

    @Test
    void status_warnsWhenTheRuntimeIsMissing() {
        when(executor.version()).thenReturn(Optional.of("2026.03.17"));
        when(executor.runtimeAvailable("deno")).thenReturn(false);

        YouTubeStatusResponse status = service.status();

        assertThat(status.available()).isTrue();
        assertThat(status.jsRuntimeAvailable()).isFalse();
        assertThat(status.message()).contains("deno");
    }

    @Test
    void cleanTitle_removesYouTubeNoise() {
        assertThat(YouTubeImportService.cleanTitle("Song (Official Video)"))
                .isEqualTo("Song");
        assertThat(YouTubeImportService.cleanTitle("Song [Lyric Video]")).isEqualTo("Song");
        assertThat(YouTubeImportService.cleanTitle("Song (Letra Oficial)")).isEqualTo("Song");
        assertThat(YouTubeImportService.cleanTitle("Song")).isEqualTo("Song");
    }

    @Test
    void cleanArtist_stripsChannelSuffixes() {
        assertThat(YouTubeImportService.cleanArtist("Artist - Topic")).isEqualTo("Artist");
        assertThat(YouTubeImportService.cleanArtist("ArtistVEVO")).isEqualTo("Artist");
        assertThat(YouTubeImportService.cleanArtist("Artist")).isEqualTo("Artist");
    }

    @Test
    void userMessage_mapsStagingPermissionFailures() {
        assertThat(YouTubeImportService.userMessage(
                new java.nio.file.AccessDeniedException("/var/lib/neonvibe/ytdlp")))
                .contains("permisos")
                .contains("/var/lib/neonvibe/ytdlp");
    }

    private YouTubeImportJobResponse awaitTerminal(String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        YouTubeImportJobResponse job = service.job(jobId);
        while (System.currentTimeMillis() < deadline && !isTerminal(job)) {
            Thread.sleep(20);
            job = service.job(jobId);
        }
        return job;
    }

    private static boolean isTerminal(YouTubeImportJobResponse job) {
        return "SUCCEEDED".equals(job.state()) || "FAILED".equals(job.state());
    }
}
