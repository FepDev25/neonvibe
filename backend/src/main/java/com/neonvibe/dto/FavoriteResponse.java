package com.neonvibe.dto;

import java.time.Instant;

import com.neonvibe.domain.FavoriteEntityType;

/**
 * Public representation of a {@link com.neonvibe.domain.Favorite}.
 */
public record FavoriteResponse(
        Long id,
        FavoriteEntityType entityType,
        Long entityId,
        Instant createdAt) {
}
