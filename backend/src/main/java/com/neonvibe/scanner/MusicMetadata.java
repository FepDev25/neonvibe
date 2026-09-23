package com.neonvibe.scanner;

/**
 * Immutable metadata extracted from an audio file.
 */
public record MusicMetadata(
        String title,
        String artist,
        String album,
        String albumArtist,
        Integer year,
        String genre,
        Integer trackNumber,
        Integer discNumber,
        Integer durationSeconds,
        Integer bitrate,
        String format,
        String mimeType,
        boolean hasLyrics) {
}
