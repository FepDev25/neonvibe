package com.neonvibe.scanner;

import java.nio.file.Path;

/**
 * Extracts {@link MusicMetadata} from an audio file. Implementations must never
 * throw for individual missing/corrupt tags: they should parse as much as
 * possible and fall back to filename parsing for the title.
 */
public interface MetadataExtractor {

    /**
     * @param path the audio file
     * @return extracted metadata (may be partial)
     */
    MusicMetadata extract(Path path);

    /**
     * Extracts embedded artwork from the audio file, or {@code null} when the
     * file has no cover art. Default no-op; implementers with tag support
     * (e.g. jaudiotagger) override it.
     */
    default EmbeddedArt extractEmbedded(Path path) {
        return null;
    }

    /**
     * Reads the file once and returns both its metadata and embedded artwork.
     *
     * <p>The default implementation composes {@link #extract} and
     * {@link #extractEmbedded}; implementations that can parse both in a single
     * pass (e.g. jaudiotagger) must override it. Reading the same file twice is a
     * known scanner bottleneck and can fail on some files, so the scanner uses
     * this method exclusively.</p>
     */
    default ExtractedFile extractFile(Path path) {
        return new ExtractedFile(extract(path), extractEmbedded(path));
    }

    /**
     * Result of a single-pass extraction.
     *
     * @param metadata    parsed metadata (never null)
     * @param embeddedArt embedded cover art, or {@code null} when absent
     */
    record ExtractedFile(MusicMetadata metadata, EmbeddedArt embeddedArt) {
    }
}
