package com.neonvibe.dto;

import com.neonvibe.domain.FavoriteEntityType;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to create a favorite.
 */
public record FavoriteRequest(
        @NotNull FavoriteEntityType entityType,
        @NotNull Long entityId) {
}
