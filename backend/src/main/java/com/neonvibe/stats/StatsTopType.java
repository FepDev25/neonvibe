package com.neonvibe.stats;

/**
 * Dimension of a "top X" stats list.
 */
public enum StatsTopType {

    TRACKS,
    ALBUMS,
    ARTISTS,
    GENRES;

    /** Lenient parse; defaults to tracks. */
    public static StatsTopType fromParam(String value) {
        if (value == null) {
            return TRACKS;
        }
        return switch (value.trim().toLowerCase()) {
            case "albums" -> ALBUMS;
            case "artists" -> ARTISTS;
            case "genres" -> GENRES;
            default -> TRACKS;
        };
    }
}
