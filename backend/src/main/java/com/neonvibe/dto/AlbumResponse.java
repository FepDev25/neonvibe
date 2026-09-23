package com.neonvibe.dto;

import java.time.Instant;

/**
 * Public representation of a {@link com.neonvibe.domain.Album}.
 */
public record AlbumResponse(
        Long id,
        String name,
        String artist,
        Integer year,
        String genre,
        String coverArtPath,
        Instant createdAt,
        long trackCount) {
}
