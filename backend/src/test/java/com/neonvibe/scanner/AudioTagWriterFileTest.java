package com.neonvibe.scanner;

import java.nio.file.Files;
import java.nio.file.Path;

import com.neonvibe.exception.TagWriteException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trips {@link AudioTagWriter}: writes tags to a real MP3 copy and reads
 * them back with the same extractor the scanner uses.
 */
class AudioTagWriterFileTest {

    private final AudioTagWriter writer = new AudioTagWriter();
    private final JAudioTaggerMetadataExtractor extractor = new JAudioTaggerMetadataExtractor();

    private Path fixture() throws Exception {
        return Path.of(getClass().getResource("/audio/tagged-sample.mp3").toURI());
    }

    @Test
    void write_persistsTagsReadableByTheScanner(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("track.mp3");
        Files.copy(fixture(), file);

        writer.write(file, new AudioTagWriter.TagValues(
                "New Title", "New Artist", "New Album", "New Album Artist",
                2024, "Jazz", 7, 2));

        MusicMetadata meta = extractor.extract(file);
        assertThat(meta.title()).isEqualTo("New Title");
        assertThat(meta.artist()).isEqualTo("New Artist");
        assertThat(meta.album()).isEqualTo("New Album");
        assertThat(meta.albumArtist()).isEqualTo("New Album Artist");
        assertThat(meta.year()).isEqualTo(2024);
        assertThat(meta.genre()).isEqualTo("Jazz");
        assertThat(meta.trackNumber()).isEqualTo(7);
        assertThat(meta.discNumber()).isEqualTo(2);
    }

    @Test
    void blankValue_clearsTheField(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("track.mp3");
        Files.copy(fixture(), file);

        // The fixture has genre "Soundtracks" and no album artist.
        writer.write(file, new AudioTagWriter.TagValues(
                null, null, null, null, null, "   ", null, null));

        MusicMetadata meta = extractor.extract(file);
        assertThat(meta.genre()).isNull();
        // null fields are left untouched.
        assertThat(meta.title()).isEqualTo("Battle (Timelapse Remix)");
    }

    @Test
    void missingFile_throwsTagWriteException(@TempDir Path dir) {
        Path missing = dir.resolve("nope.mp3");

        assertThatThrownBy(() -> writer.write(missing,
                new AudioTagWriter.TagValues("T", null, null, null, null, null, null, null)))
                .isInstanceOf(TagWriteException.class);
    }
}
