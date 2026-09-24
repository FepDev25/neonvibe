package com.neonvibe.infra;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CoverArtStore}: path building, read/write round-trip and
 * extension-based lookup for covers and lyrics.
 */
class CoverArtStoreTest {

    @TempDir
    Path tempDir;

    private CoverArtStore store;

    @BeforeEach
    void setUp() {
        store = new CoverArtStore(
                tempDir.resolve("covers").toString(),
                tempDir.resolve("lyrics").toString());
    }

    @Test
    void constructor_createsCategoryDirectories() {
        assertThat(Files.isDirectory(tempDir.resolve("covers/album"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("covers/artist"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("covers/embedded"))).isTrue();
        assertThat(Files.isDirectory(tempDir.resolve("lyrics"))).isTrue();
    }

    @Test
    void fileFor_buildsCategoryPath() {
        Path path = store.fileFor("album", 42L, "jpg");

        assertThat(path.getFileName().toString()).isEqualTo("42.jpg");
        assertThat(path.getParent().getFileName().toString()).isEqualTo("album");
    }

    @Test
    void writeAndRead_roundTrip() {
        Path file = store.fileFor("album", 1L, "png");

        store.write(file, new byte[]{7, 7, 7});

        assertThat(store.read(file)).containsExactly(7, 7, 7);
    }

    @Test
    void find_returnsExistingFileByExtension() {
        store.write(store.fileFor("artist", 5L, "webp"), new byte[]{1});

        assertThat(store.find("artist", 5L)).isPresent();
        assertThat(store.find("artist", 5L).get().getFileName().toString()).isEqualTo("5.webp");
    }

    @Test
    void find_unknownIdOrCategory_returnsEmpty() {
        assertThat(store.find("album", 999L)).isEmpty();
        assertThat(store.find("does-not-exist", 1L)).isEmpty();
    }

    @Test
    void findLyrics_matchesTrackIdAndKnownExtension() {
        store.write(store.lyricsFile(3L, true), "[00:01.00] hi".getBytes());

        assertThat(store.findLyrics(3L)).isPresent();
        assertThat(store.findLyrics(3L).get().getFileName().toString()).isEqualTo("3.lrc");
        assertThat(store.findLyrics(99L)).isEmpty();
    }

    @Test
    void findLyrics_ignoresUnknownExtension() {
        store.write(tempDir.resolve("lyrics/4.bin"), new byte[]{1});

        assertThat(store.findLyrics(4L)).isEmpty();
    }

    @Test
    void lyricsFile_usesExtensionBasedOnSynced() {
        assertThat(store.lyricsFile(1L, true).getFileName().toString()).isEqualTo("1.lrc");
        assertThat(store.lyricsFile(1L, false).getFileName().toString()).isEqualTo("1.txt");
    }
}
