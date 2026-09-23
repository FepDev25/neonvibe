package com.neonvibe.dto;

/**
 * Public (no-auth) representation of a track: excludes filesystem paths and
 * other internal details. Used by shared-playlist views.
 */
public record PublicTrackResponse(
        Long id,
        String title,
        String artist,
        String album,
        Integer year,
        String genre,
        Integer trackNumber,
        Integer durationSeconds,
        boolean hasLyrics) {
}
