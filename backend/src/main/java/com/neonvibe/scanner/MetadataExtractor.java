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
}
