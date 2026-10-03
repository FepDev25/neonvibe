package com.neonvibe.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Public representation of a {@link com.neonvibe.domain.Track}.
 *
 * <p>{@code filePath} is kept for internal/mapper use but excluded from the JSON
 * payload: exposing absolute server paths to any authenticated user is an
 * information leak.</p>
 */
public record TrackResponse(
        Long id,
        @JsonIgnore String filePath,
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
        boolean hasLyrics,
        @JsonIgnore String coverArtPath,
        boolean isAvailable,
        Instant createdAt,
        Instant updatedAt) {
}
