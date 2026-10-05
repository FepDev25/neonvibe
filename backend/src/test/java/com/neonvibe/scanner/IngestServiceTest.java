package com.neonvibe.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Track;
import com.neonvibe.service.CoverArtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ingest pipeline with real audio (the tagged MP3 fixture), a real filesystem
 * and mocked persistence/cover services.
 */
class IngestServiceTest {

    @TempDir
    Path tempDir;

    private final JAudioTaggerMetadataExtractor extractor = new JAudioTaggerMetadataExtractor();
    private final LibrarySyncService sync = mock(LibrarySyncService.class);
    private final CoverArtService coverArt = mock(CoverArtService.class);

    private ScannerConfig config;
    private IngestService service;

    @BeforeEach
    void setUp() {
        config = new ScannerConfig();
        config.setPaths(List.of(tempDir.toString()));
        config.setSupportedFormats(List.of("mp3", "flac"));
        service = new IngestService(config, extractor, sync, coverArt);

        when(sync.upsert(any(), any(), any())).thenAnswer(inv -> {
            Path path = inv.getArgument(0);
            Album album = Album.builder().id(42L).name("Album").build();
            return Track.builder().id(1L).filePath(path.toString()).albumEntity(album).build();
        });
    }

    private byte[] fixture() throws Exception {
        return Files.readAllBytes(
                Path.of(getClass().getResource("/audio/tagged-sample.mp3").toURI()));
    }

    private Path staging() throws IOException {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        return staging;
    }

    @Test
    void ingestUpload_movesFileIntoArtistAlbumFolderAndUpserts() throws Exception {
        Path staging = staging();
        Files.write(staging.resolve("song.mp3"), fixture());

        IngestService.IngestResult result = service.ingestUpload(staging);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        // The uploaded staging dir is wiped.
        assertThat(staging).doesNotExist();
        // The file now lives under <root>/<Artist>/... (fixture artist).
        try (var walk = Files.walk(tempDir)) {
            assertThat(walk.filter(Files::isRegularFile)
                    .anyMatch(p -> p.toString().contains("Nobuo Uematsu")))
                    .isTrue();
        }
        verify(sync).upsert(any(Path.class), any(MusicMetadata.class), any());
    }

    @Test
    void ingestUpload_extractsZipAndRejectsZipSlipEntry() throws Exception {
        Path staging = staging();
        Path zip = staging.resolve("album.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            // Valid audio entry first so it is extracted before the malicious one.
            out.putNextEntry(new ZipEntry("album/song.mp3"));
            out.write(fixture());
            out.closeEntry();
            // Path-traversal entry: must never be written outside the target.
            out.putNextEntry(new ZipEntry("../escape.mp3"));
            out.write(fixture());
            out.closeEntry();
        }

        IngestService.IngestResult result = service.ingestUpload(staging);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(tempDir.resolveSibling("escape.mp3")).doesNotExist();
        assertThat(staging).doesNotExist();
    }

    @Test
    void ingestUpload_appliesSidecarCoverToTheAlbum() throws Exception {
        Path staging = staging();
        Files.write(staging.resolve("song.mp3"), fixture());
        Files.write(staging.resolve("cover.jpg"), new byte[]{1, 2, 3});

        service.ingestUpload(staging);

        verify(coverArt).saveManualAlbum(eq(42L), eq(new byte[]{1, 2, 3}), eq("image/jpeg"));
    }

    @Test
    void ingestUpload_ignoresNonCoverImages() throws Exception {
        Path staging = staging();
        Files.write(staging.resolve("song.mp3"), fixture());
        Files.write(staging.resolve("band-photo.jpg"), new byte[]{1});

        service.ingestUpload(staging);

        verify(coverArt, never()).saveManualAlbum(anyLong(), any(), any());
    }

    @Test
    void ingestUpload_doesNotOverwriteAnExistingLibraryFile() throws Exception {
        Path staging = staging();
        Files.write(staging.resolve("song.mp3"), fixture());

        // The fixture maps to <artist>/<album>/song.mp3; pre-create that file.
        Path destDir = tempDir.resolve("Nobuo Uematsu")
                .resolve("FINAL FANTASY Special Soundtrack _Timelapse Remix_");
        Files.createDirectories(destDir);
        Files.writeString(destDir.resolve("song.mp3"), "existing");

        IngestService.IngestResult result = service.ingestUpload(staging);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(Files.readString(destDir.resolve("song.mp3"))).isEqualTo("existing");
        assertThat(destDir.resolve("song (1).mp3")).exists();
    }

    @Test
    void safeUploadName_stripsPathsAndUnsafeChars() {
        assertThat(IngestService.safeUploadName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(IngestService.safeUploadName("my song?.mp3")).isEqualTo("my song_.mp3");
        assertThat(IngestService.safeUploadName(null)).isEqualTo("upload");
        assertThat(IngestService.safeUploadName("..")).isEqualTo("upload");
    }
}
