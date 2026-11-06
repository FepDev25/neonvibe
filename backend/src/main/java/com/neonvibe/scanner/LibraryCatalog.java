package com.neonvibe.scanner;

/**
 * Reserved values and normalization rules for the library catalog.
 *
 * <p>Every track belongs to an album. Loose tracks (no album tag) are grouped
 * under a single, reserved album named {@value #SINGLES_ALBUM} with no artist,
 * so they are visible in the Albums section like any other album while each
 * track keeps its own artist.</p>
 *
 * <p>The rules are applied in three places that must stay in sync: metadata
 * extraction/upsert, ingest destination folders and metadata edits. Keeping
 * them here avoids the album entity and the physical folder disagreeing.</p>
 */
public final class LibraryCatalog {

    /** Name of the album that groups loose tracks. */
    public static final String SINGLES_ALBUM = "Singles";

    private LibraryCatalog() {
    }

    /**
     * Canonical album name: blank values and the legacy tags {@code "single"} /
     * {@code "singles"} (case-insensitive) become {@value #SINGLES_ALBUM}; any
     * other value is trimmed and kept as-is.
     */
    public static String normalizeAlbumName(String album) {
        if (album == null || album.isBlank()) {
            return SINGLES_ALBUM;
        }
        String trimmed = album.trim();
        if (trimmed.equalsIgnoreCase("single") || trimmed.equalsIgnoreCase("singles")) {
            return SINGLES_ALBUM;
        }
        return trimmed;
    }

    /** Whether the given album name is the reserved singles album. */
    public static boolean isSingles(String albumName) {
        return albumName != null && albumName.trim().equalsIgnoreCase(SINGLES_ALBUM);
    }

    /**
     * The global singles album is identified by its name <em>and</em> having no
     * artist; a real artist album named "Singles" keeps its own artist.
     */
    public static boolean isReservedSingles(String albumName, String albumArtist) {
        return isSingles(albumName) && albumArtist == null;
    }
}
