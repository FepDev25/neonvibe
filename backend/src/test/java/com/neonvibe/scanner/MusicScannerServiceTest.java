package com.neonvibe.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MusicScannerService} orchestration with mocked
 * collaborators (no Spring context, no filesystem scan). Real files are created
 * in a temp dir because {@code isSupportedFile} checks they are regular files.
 */
class MusicScannerServiceTest {

    @TempDir
    Path tempDir;

    private final ScannerConfig config = config();
    private final MetadataExtractor extractor = mock(MetadataExtractor.class);
    private final LibrarySyncService sync = mock(LibrarySyncService.class);
    private final ScannerStatus status = new ScannerStatus();
    private final FileWatcherService watcher = mock(FileWatcherService.class);
    private final com.neonvibe.websocket.ScannerWsBridge wsBridge = mock(com.neonvibe.websocket.ScannerWsBridge.class);
    private final MusicScannerService scanner = new MusicScannerService(config, extractor, sync, status, watcher, wsBridge);

    private Path mp3;
    private Path flac;
    private Path txt;

    private ScannerConfig config() {
        ScannerConfig cfg = new ScannerConfig();
        cfg.setSupportedFormats(java.util.List.of("mp3", "flac"));
        return cfg;
    }

    @BeforeEach
    void setUpFiles() throws IOException {
        mp3 = tempDir.resolve("01 - Song.mp3");
        flac = tempDir.resolve("02 - Flac.flac");
        txt = tempDir.resolve("readme.txt");
        Files.writeString(mp3, "dummy");
        Files.writeString(flac, "dummy");
        Files.writeString(txt, "hello");
    }

    @Test
    void isSupportedFile_acceptsConfiguredExtensions() {
        assertTrue(scanner.isSupportedFile(mp3));
        assertTrue(scanner.isSupportedFile(flac));
        assertFalse(scanner.isSupportedFile(txt));
        assertFalse(scanner.isSupportedFile(null));
    }

    @Test
    void onFileChanged_persistsSupportedFile() {
        when(extractor.extractFile(mp3)).thenReturn(new MetadataExtractor.ExtractedFile(
                new MusicMetadata("T", "A", "Al", null, null,
                        null, null, null, null, null, "mp3", "audio/mpeg", false),
                null));

        scanner.onFileChanged(mp3);

        verify(sync).upsert(eq(mp3), any(MusicMetadata.class), any());
        assertTrue(status.getProcessed() >= 1);
    }

    @Test
    void onFileChanged_unsupportedFile_isCountedButNotPersisted() {
        scanner.onFileChanged(txt);
        verify(sync, never()).upsert(any(), any(), any());
    }

    @Test
    void onFileDeleted_marksUnavailable() {
        scanner.onFileDeleted(tempDir.resolve("gone.mp3"));
        verify(sync).markUnavailable(tempDir.resolve("gone.mp3").toString());
    }

    @Test
    void onFileChanged_failedFile_trackedAsFailure() {
        when(extractor.extractFile(mp3)).thenThrow(new RuntimeException("corrupt tags"));
        scanner.onFileChanged(mp3);
        assertTrue(status.getFailed() > 0);
        assertTrue(status.getFailedFiles().containsKey(mp3.toString()));
    }

    private MetadataExtractor.ExtractedFile extracted(String title) {
        return new MetadataExtractor.ExtractedFile(
                new MusicMetadata(title, "A", "Al", null, null, null, null, null, null, null,
                        "mp3", "audio/mpeg", false),
                null);
    }

    @Test
    void scanAll_walksRootAndProcessesSupportedFiles() {
        config.setPaths(java.util.List.of(tempDir.toString()));
        when(extractor.extractFile(any())).thenReturn(extracted("T"));

        scanner.scanAll();

        verify(sync, org.mockito.Mockito.times(2)).upsert(any(), any(MusicMetadata.class), any());
        assertTrue(status.getProcessed() >= 2);
        assertFalse(status.isRunning());
        verify(wsBridge).publishProgress(status);
    }

    @Test
    void scanAll_missingRoot_completesWithoutProcessing() {
        config.setPaths(java.util.List.of(tempDir.resolve("nope").toString()));

        scanner.scanAll();

        verify(sync, never()).upsert(any(), any(), any());
        assertFalse(status.isRunning());
    }

    @Test
    void triggerManualScan_returnsIdleStatus() {
        config.setPaths(java.util.List.of(tempDir.toString()));
        when(extractor.extractFile(any())).thenReturn(extracted("T"));

        ScannerStatus result = scanner.triggerManualScan();

        assertFalse(result.isRunning());
    }

    @Test
    void onFileDeleted_failureIsRecorded() {
        Path gone = tempDir.resolve("gone.mp3");
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(sync).markUnavailable(gone.toString());

        scanner.onFileDeleted(gone);

        assertTrue(status.getFailed() > 0);
        assertTrue(status.getFailedFiles().containsKey(gone.toString()));
    }

    @Test
    void scanAsync_processesInBackground() throws InterruptedException {
        config.setPaths(java.util.List.of(tempDir.toString()));
        when(extractor.extractFile(any())).thenReturn(extracted("T"));

        scanner.scanAsync();

        long deadline = System.currentTimeMillis() + 5_000;
        while (status.getProcessed() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(status.getProcessed() >= 2);
    }
}
