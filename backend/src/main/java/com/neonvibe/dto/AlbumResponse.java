package com.neonvibe.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Public representation of a {@link com.neonvibe.domain.Album}.
 *
 * <p>{@code coverArtPath} is a server filesystem path and is excluded from JSON
 * (the client uses the {@code /albums/{id}/cover} endpoint instead).</p>
 */
public record AlbumResponse(
        Long id,
        String name,
        String artist,
        Integer year,
        String genre,
        @JsonIgnore String coverArtPath,
        Instant createdAt,
        long trackCount) {
}
