package com.neonvibe.scanner;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads a real, tagged MP3 fixture to exercise the jaudiotagger tag path
 * (ID3v2.3): title/artist/album/genre/track number, bitrate, format/mime and
 * the single-pass embedded-artwork extraction.
 *
 * <p>The fixture is a 3-second clip with the original ID3 tags preserved and no
 * embedded cover art.</p>
 */
class JAudioTaggerMetadataExtractorFileTest {

    private final JAudioTaggerMetadataExtractor extractor = new JAudioTaggerMetadataExtractor();

    private Path fixture() throws URISyntaxException {
        return Path.of(getClass().getResource("/audio/tagged-sample.mp3").toURI());
    }

    @Test
    void extractFile_readsId3TagsInOnePass() throws Exception {
        MetadataExtractor.ExtractedFile result = extractor.extractFile(fixture());

        MusicMetadata meta = result.metadata();
        assertThat(meta.title()).isEqualTo("Battle (Timelapse Remix)");
        assertThat(meta.artist()).isEqualTo("Nobuo Uematsu");
        assertThat(meta.album()).isEqualTo("FINAL FANTASY Special Soundtrack \"Timelapse Remix\"");
        assertThat(meta.genre()).isEqualTo("Soundtracks");
        assertThat(meta.trackNumber()).isEqualTo(3);
        assertThat(meta.albumArtist()).isNull();
        assertThat(meta.year()).isNull();
        assertThat(meta.format()).isEqualTo("mp3");
        assertThat(meta.mimeType()).isEqualTo("audio/mpeg");
        assertThat(meta.durationSeconds()).isPositive();
        assertThat(meta.bitrate()).isPositive();
        assertThat(meta.hasLyrics()).isFalse();
        // The fixture carries no embedded cover art.
        assertThat(result.embeddedArt()).isNull();
    }

    @Test
    void extract_delegatesToTheSinglePassExtraction() throws Exception {
        assertThat(extractor.extract(fixture()).title()).isEqualTo("Battle (Timelapse Remix)");
    }

    @Test
    void extractFile_readsEmbeddedArtwork() throws Exception {
        MetadataExtractor.ExtractedFile result = extractor.extractFile(
                Path.of(getClass().getResource("/audio/tagged-with-art.mp3").toURI()));

        assertThat(result.embeddedArt()).isNotNull();
        assertThat(result.embeddedArt().extension()).isEqualTo(EmbeddedArt.EXT_PNG);
        assertThat(result.embeddedArt().data()).isNotEmpty();
    }
}
