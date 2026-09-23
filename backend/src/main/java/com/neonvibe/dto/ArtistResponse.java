package com.neonvibe.dto;

import java.time.Instant;

/**
 * Public representation of a {@link com.neonvibe.domain.Artist}.
 */
public record ArtistResponse(
        Long id,
        String name,
        Instant createdAt) {
}
