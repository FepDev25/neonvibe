package com.neonvibe.dto;

import java.time.Instant;

/**
 * Public representation of a {@link com.neonvibe.domain.Track}.
 */
public record TrackResponse(
        Long id,
        String filePath,
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
        String coverArtPath,
        boolean isAvailable,
        Instant createdAt,
        Instant updatedAt) {
}
