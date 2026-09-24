package com.neonvibe.scanner;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link JAudioTaggerMetadataExtractor}, focused on the
 * filename-parsing fallback and the tolerant handling of missing/corrupt files.
 */
class MetadataExtractorTest {

    private final JAudioTaggerMetadataExtractor extractor = new JAudioTaggerMetadataExtractor();

    @Test
    void titleFromFilename_artistDashTitle() {
        assertEquals("Song Title", extractor.titleFromFilename(Path.of("/music/Artist - Song Title.mp3")));
    }

    @Test
    void titleFromFilename_numberDashTitle() {
        assertEquals("Second Track", extractor.titleFromFilename(Path.of("/music/02 - Second Track.flac")));
    }

    @Test
    void titleFromFilename_artistAlbumTitle() {
        assertEquals("Track Name", extractor.titleFromFilename(Path.of("/music/Artist - Album - Track Name.ogg")));
    }

    @Test
    void titleFromFilename_plain() {
        assertEquals("JustAName", extractor.titleFromFilename(Path.of("/music/JustAName.m4a")));
    }

    @Test
    void extract_nonExistentFile_returnsFallbackWithoutThrowing() {
        MusicMetadata meta = extractor.extract(Path.of("/nonexistent/02 - Fallback Title.mp3"));
        assertEquals("Fallback Title", meta.title());
        assertNull(meta.artist());
        assertNull(meta.album());
        assertEquals("mp3", meta.format());
        assertEquals("audio/mpeg", meta.mimeType());
        assertFalse(meta.hasLyrics());
    }

    @Test
    void extractFile_nonExistentFile_returnsFallbackAndNullArt() {
        MetadataExtractor.ExtractedFile result =
                extractor.extractFile(Path.of("/nonexistent/02 - Fallback Title.mp3"));

        assertEquals("Fallback Title", result.metadata().title());
        assertNull(result.embeddedArt());
    }

    @Test
    void extractEmbedded_nonExistentFile_returnsNullWithoutThrowing() {
        assertNull(extractor.extractEmbedded(Path.of("/nonexistent/x.mp3")));
    }
}
